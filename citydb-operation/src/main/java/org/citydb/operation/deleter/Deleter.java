/*
 * citydb-tool - Command-line tool for the 3D City Database
 * https://www.3dcitydb.org/
 *
 * Copyright 2022-2024
 * virtualcitysystems GmbH, Germany
 * https://vc.systems/
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.citydb.operation.deleter;

import org.citydb.core.concurrent.CountLatch;
import org.citydb.core.concurrent.ExecutorHelper;
import org.citydb.database.adapter.DatabaseAdapter;
import org.citydb.operation.deleter.util.DeleteLogger;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

public class Deleter {
    private ExecutorService service;
    private Connection connection;
    private ThreadLocal<DeleteHelper> contexts;
    private Set<DeleteHelper> helpers;
    private DeleteLogger logger;
    private CountLatch countLatch;
    private Throwable exception;
    private boolean autoCommit = false;

    private volatile State state = State.SESSION_NOT_STARTED;
    private volatile boolean shouldRun;

    public enum State {
        SESSION_NOT_STARTED,
        SESSION_STARTED,
        SESSION_COMMITTED,
        SESSION_ABORTED
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

    public boolean isAutoCommit() {
        return autoCommit;
    }

    public Deleter setAutoCommit(boolean autoCommit) {
        this.autoCommit = autoCommit;
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
        }

        Objects.requireNonNull(adapter, "The database adapter must not be null.");
        Objects.requireNonNull(options, "The delete options must not be null.");

        try {
            connection = adapter.getPool().getConnection(false);
            helpers = ConcurrentHashMap.newKeySet();
            service = ExecutorHelper.newFixedAndBlockingThreadPool(options.getNumberOfThreads() > 0 ?
                    options.getNumberOfThreads() : 1);

            countLatch = new CountLatch();
            contexts = ThreadLocal.withInitial(() -> {
                try {
                    DeleteHelper helper = new DeleteHelper(adapter, connection, options, logger, autoCommit);
                    helpers.add(helper);
                    return helper;
                } catch (Exception e) {
                    shouldRun = false;
                    throw new RuntimeException(e);
                }
            });

            state = State.SESSION_STARTED;
            shouldRun = true;
            return this;
        } catch (Exception e) {
            throw new DeleteException("Failed to start delete session.", e);
        }
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
        }

        return result;
    }

    public void commitSession() throws DeleteException {
        if (state == State.SESSION_NOT_STARTED
                || state == State.SESSION_COMMITTED
                || state == State.SESSION_ABORTED) {
            return;
        }

        try {
            state = State.SESSION_COMMITTED;
            countLatch.await();

            for (DeleteHelper helper : helpers) {
                countLatch.increment();
                service.execute(() -> {
                    try {
                        helper.executeBatch(true, true);
                        helper.close();
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
        } catch (Throwable e) {
            shouldRun = false;
            throw new DeleteException("Failed to commit delete session.", e);
        } finally {
            service.shutdown();
            close();
        }
    }

    public void abortSession() throws DeleteException {
        if (state == State.SESSION_NOT_STARTED
                || state == State.SESSION_COMMITTED
                || state == State.SESSION_ABORTED) {
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
            service.shutdown();
            close();
        }
    }

    private void close() {
        try {
            connection.close();
        } catch (SQLException e) {
            //
        }
    }
}
