/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.operation.importer;

import org.citydb.core.cache.PersistentMapStore;
import org.citydb.core.concurrent.CountLatch;
import org.citydb.core.concurrent.ExecutorHelper;
import org.citydb.core.function.CheckedConsumer;
import org.citydb.core.function.CheckedSupplier;
import org.citydb.database.adapter.DatabaseAdapter;
import org.citydb.model.feature.Feature;
import org.citydb.model.feature.FeatureDescriptor;
import org.citydb.model.property.Property;
import org.citydb.model.property.PropertyDescriptor;
import org.citydb.operation.importer.reference.ReferenceManager;
import org.citydb.operation.importer.util.ImportLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.Connection;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadPoolExecutor;

public class Importer implements AutoCloseable {
    private final Logger logger = LoggerFactory.getLogger(Importer.class);
    private ThreadPoolExecutor service;
    private PersistentMapStore store;
    private ReferenceManager referenceManager;
    private ThreadLocal<ImportHelper> contexts;
    private Set<ImportHelper> helpers;
    private ImportLogger importLogger;
    private Connection connection;
    private CountLatch countLatch;
    private Throwable exception;
    private TransactionMode transactionMode = TransactionMode.COMMIT_BATCHES;

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

    private Importer() {
    }

    public static Importer newInstance() {
        return new Importer();
    }

    public Optional<ImportLogger> getImportLogger() {
        return Optional.ofNullable(importLogger);
    }

    public Importer setImportLogger(ImportLogger importLogger) {
        this.importLogger = importLogger;
        return this;
    }

    public TransactionMode getTransactionMode() {
        return transactionMode;
    }

    public Importer setTransactionMode(TransactionMode transactionMode) {
        this.transactionMode = transactionMode;
        return this;
    }

    public Optional<Connection> getConnection() {
        return Optional.ofNullable(connection);
    }

    public Importer setConnection(Connection connection) {
        if (state == State.SESSION_STARTED) {
            throw new IllegalStateException("Illegal to set the connection during a session.");
        }

        this.connection = connection;
        return this;
    }

    public State getState() {
        return state;
    }

    public boolean wasSuccessful() {
        return shouldRun;
    }

    public Importer startSession(DatabaseAdapter adapter, ImportOptions options) throws ImportException {
        if (state == State.SESSION_STARTED) {
            return this;
        } else if (state == State.CLOSED) {
            throw new ImportException("Illegal to start a session after the importer has been closed.");
        }

        Objects.requireNonNull(adapter, "The database adapter must not be null.");
        Objects.requireNonNull(options, "The import options must not be null.");

        try {
            if (store == null) {
                store = PersistentMapStore.builder()
                        .tempDirectory(options.getTempDirectory().orElse(null))
                        .build();
                logger.debug("Initialized cache for resolving references at {}.", store.getBackingFile());
            } else {
                store.clear();
            }
        } catch (IOException e) {
            throw new ImportException("Failed to initialize local cache.", e);
        }

        exception = null;
        referenceManager = ReferenceManager.newInstance(adapter, store, options, connection);
        updateService(connection != null
                ? 1
                : options.getNumberOfThreads() > 0
                ? options.getNumberOfThreads()
                : Math.max(2, Runtime.getRuntime().availableProcessors()));

        helpers = ConcurrentHashMap.newKeySet();
        countLatch = new CountLatch();
        contexts = ThreadLocal.withInitial(() -> {
            try {
                ImportHelper helper = new ImportHelper(adapter, options, referenceManager, store,
                        importLogger, connection, transactionMode);
                helpers.add(helper);
                return helper;
            } catch (Exception e) {
                shouldRun = false;
                throw new RuntimeException("Failed to start import session.", e);
            }
        });

        state = State.SESSION_STARTED;
        shouldRun = true;
        return this;
    }

    public CompletableFuture<FeatureDescriptor> importFeature(Feature feature) {
        return doImport(() -> contexts.get().importFeature(feature));
    }

    public CompletableFuture<PropertyDescriptor> importProperty(Property<?> property, long featureId) {
        return doImport(() -> contexts.get().importProperty(property, featureId));
    }

    private <T> CompletableFuture<T> doImport(CheckedSupplier<T, Throwable> supplier) {
        CompletableFuture<T> result = new CompletableFuture<>();
        if (shouldRun) {
            if (state == State.SESSION_STARTED) {
                countLatch.increment();
                service.execute(() -> {
                    try {
                        result.complete(supplier.get());
                    } catch (Throwable e) {
                        shouldRun = false;
                        result.completeExceptionally(e);
                    } finally {
                        countLatch.decrement();
                    }
                });
            } else {
                result.completeExceptionally(new ImportException("Illegal to import data outside a session."));
            }
        } else {
            result.cancel(true);
        }

        return result;
    }

    public void flush() throws ImportException {
        if (state != State.SESSION_STARTED) {
            return;
        }

        try {
            execute(helper -> helper.executeBatch(true, false));
        } catch (Throwable e) {
            shouldRun = false;
            throw new ImportException("Failed to flush import session.", e);
        }
    }

    public void completeSession() throws ImportException {
        if (state != State.SESSION_STARTED) {
            return;
        } else if (connection == null && transactionMode == TransactionMode.ROLLBACK) {
            throw new ImportException("Illegal to complete a session in rollback mode.");
        }

        countLatch.await();
        if (!shouldRun) {
            abortSession();
            throw new ImportException("Failed to complete import session because an import has failed.");
        }

        try {
            state = State.SESSION_COMPLETED;
            execute(helper -> {
                helper.executeBatch(true, connection == null);
                helper.close();
            });

            try {
                referenceManager.resolveReferences();
            } finally {
                referenceManager.close();
            }
        } catch (Throwable e) {
            shouldRun = false;
            throw new ImportException("Failed to complete import session.", e);
        } finally {
            endSession();
        }
    }

    public void abortSession() throws ImportException {
        if (state != State.SESSION_STARTED) {
            return;
        }

        try {
            state = State.SESSION_ABORTED;
            countLatch.await();
            for (ImportHelper helper : helpers) {
                helper.close();
            }

            referenceManager.close();
        } catch (Exception e) {
            shouldRun = false;
            throw new ImportException("Failed to abort import session.", e);
        } finally {
            endSession();
        }
    }

    @Override
    public void close() throws ImportException {
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

            if (store != null) {
                store.close();
            }
        }
    }

    private void execute(CheckedConsumer<ImportHelper, Throwable> action) throws Throwable {
        countLatch.await();
        for (ImportHelper helper : helpers) {
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
            referenceManager.close();
        } catch (ImportException e) {
            //
        } finally {
            referenceManager = null;
            helpers = null;
            contexts = null;
            store.clear();
        }
    }
}
