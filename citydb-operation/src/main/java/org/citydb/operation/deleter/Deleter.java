/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.operation.deleter;

import org.citydb.core.concurrent.CountLatch;
import org.citydb.core.concurrent.ExecutorHelper;
import org.citydb.core.function.CheckedConsumer;
import org.citydb.database.adapter.DatabaseAdapter;
import org.citydb.operation.deleter.util.DeleteLogger;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadPoolExecutor;

public class Deleter implements AutoCloseable {
    private ThreadPoolExecutor service;
    private Connection externalConnection;
    private Connection connection;
    private ThreadLocal<DeleteHelper> contexts;
    private Set<DeleteHelper> helpers;
    private DeleteLogger logger;
    private CountLatch countLatch;
    private Throwable exception;
    private TransactionMode transactionMode = TransactionMode.COMMIT_SESSION;

    private volatile State state = State.SESSION_NOT_STARTED;
    private volatile boolean shouldRun;

    public enum TransactionMode {
        COMMIT_BATCHES,
        COMMIT_SESSION,
        ROLLBACK
    }

    public enum State {
        SESSION_NOT_STARTED,
        SESSION_STARTED,
        SESSION_COMPLETED,
        SESSION_ABORTED,
        CLOSED
    }

    private Deleter() {
    }

    public static Deleter newInstance() {
        return new Deleter();
    }

    public Optional<DeleteLogger> getDeleteLogger() {
        return Optional.ofNullable(logger);
    }

    public Deleter setDeleteLogger(DeleteLogger logger) {
        this.logger = logger;
        return this;
    }

    public TransactionMode getTransactionMode() {
        return transactionMode;
    }

    public Deleter setTransactionMode(TransactionMode transactionMode) {
        this.transactionMode = transactionMode;
        return this;
    }

    public Optional<Connection> getConnection() {
        return Optional.ofNullable(externalConnection);
    }

    public Deleter setConnection(Connection connection) {
        if (state == State.SESSION_STARTED) {
            throw new IllegalStateException("Illegal to set the connection during a session.");
        }

        externalConnection = connection;
        return this;
    }

    public State getState() {
        return state;
    }

    public boolean wasSuccessful() {
        return shouldRun;
    }

    public Deleter startSession(DatabaseAdapter adapter, DeleteOptions options) throws DeleteException {
        if (state == State.SESSION_STARTED) {
            return this;
        } else if (state == State.CLOSED) {
            throw new DeleteException("Illegal to start a session after the deleter has been closed.");
        }

        Objects.requireNonNull(adapter, "The database adapter must not be null.");
        Objects.requireNonNull(options, "The delete options must not be null.");

        boolean ownsConnection = externalConnection == null;
        try {
            connection = ownsConnection ? adapter.getPool().getConnection(false) : externalConnection;
        } catch (SQLException e) {
            throw new DeleteException("Failed to connect to the database.", e);
        }

        exception = null;
        updateService(ownsConnection && options.getNumberOfThreads() > 0
                ? options.getNumberOfThreads()
                : 1);

        helpers = ConcurrentHashMap.newKeySet();
        countLatch = new CountLatch();
        contexts = ThreadLocal.withInitial(() -> {
            try {
                DeleteHelper helper = new DeleteHelper(adapter, connection, ownsConnection, options,
                        logger, transactionMode);
                helpers.add(helper);
                return helper;
            } catch (Exception e) {
                shouldRun = false;
                throw new RuntimeException("Failed to start delete session.", e);
            }
        });

        state = State.SESSION_STARTED;
        shouldRun = true;
        return this;
    }

    public CompletableFuture<Boolean> deleteFeature(long id) {
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        if (shouldRun) {
            if (state == State.SESSION_STARTED) {
                countLatch.increment();
                service.execute(() -> {
                    try {
                        contexts.get().deleteFeature(id);
                        result.complete(true);
                    } catch (Throwable e) {
                        shouldRun = false;
                        result.completeExceptionally(e);
                    } finally {
                        countLatch.decrement();
                    }
                });
            } else {
                result.completeExceptionally(new DeleteException("Illegal to delete data outside a session."));
            }
        } else {
            result.cancel(true);
        }

        return result;
    }

    public void flush() throws DeleteException {
        if (state != State.SESSION_STARTED) {
            return;
        }

        try {
            execute(helper -> helper.executeBatch(true, false));
        } catch (Throwable e) {
            shouldRun = false;
            throw new DeleteException("Failed to flush delete session.", e);
        }
    }

    public void completeSession() throws DeleteException {
        if (state != State.SESSION_STARTED) {
            return;
        } else if (externalConnection == null && transactionMode == TransactionMode.ROLLBACK) {
            throw new DeleteException("Illegal to complete a session in rollback mode.");
        }

        countLatch.await();
        if (!shouldRun) {
            abortSession();
            throw new DeleteException("Failed to complete delete session because a delete has failed.");
        }

        try {
            state = State.SESSION_COMPLETED;
            execute(helper -> {
                helper.executeBatch(true, externalConnection == null);
                helper.close();
            });
        } catch (Throwable e) {
            shouldRun = false;
            throw new DeleteException("Failed to complete delete session.", e);
        } finally {
            endSession();
        }
    }

    public void abortSession() throws DeleteException {
        if (state != State.SESSION_STARTED) {
            return;
        }

        try {
            state = State.SESSION_ABORTED;
            countLatch.await();
            for (DeleteHelper helper : helpers) {
                helper.close();
            }
        } catch (Exception e) {
            shouldRun = false;
            throw new DeleteException("Failed to abort delete session.", e);
        } finally {
            endSession();
        }
    }

    @Override
    public void close() throws DeleteException {
        if (state == State.CLOSED) {
            return;
        }

        try {
            abortSession();
        } finally {
            state = State.CLOSED;
            if (service != null) {
                service.shutdown();
            }
        }
    }

    private void execute(CheckedConsumer<DeleteHelper, Throwable> action) throws Throwable {
        countLatch.await();
        for (DeleteHelper helper : helpers) {
            countLatch.increment();
            service.execute(() -> {
                try {
                    action.accept(helper);
                } catch (Throwable e) {
                    exception = e;
                } finally {
                    countLatch.decrement();
                }
            });
        }

        countLatch.await();
        if (exception != null) {
            throw exception;
        }
    }

    private void updateService(int threads) {
        if (service != null && service.getMaximumPoolSize() != threads) {
            service.shutdown();
            service = null;
        }

        if (service == null) {
            service = ExecutorHelper.newFixedAndBlockingThreadPool(threads);
        }
    }

    private void endSession() {
        try {
            if (connection != null && connection != externalConnection) {
                connection.close();
            }
        } catch (SQLException e) {
            //
        } finally {
            connection = null;
            helpers = null;
            contexts = null;
        }
    }
}
