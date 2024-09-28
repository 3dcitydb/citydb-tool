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

package org.citydb.operation.importer;

import org.citydb.core.concurrent.CountLatch;
import org.citydb.core.concurrent.ExecutorHelper;
import org.citydb.database.adapter.DatabaseAdapter;
import org.citydb.model.feature.Feature;
import org.citydb.model.feature.FeatureDescriptor;
import org.citydb.operation.importer.reference.ReferenceManager;
import org.citydb.operation.importer.util.ImportLogger;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

public class Importer {
    private ExecutorService service;
    private ReferenceManager referenceManager;
    private ThreadLocal<ImportHelper> contexts;
    private Set<ImportHelper> helpers;
    private ImportLogger logger;
    private CountLatch countLatch;
    private Throwable exception;
    private boolean autoCommit = true;

    private volatile State state = State.SESSION_NOT_STARTED;
    private volatile boolean shouldRun;

    public enum State {
        SESSION_NOT_STARTED,
        SESSION_STARTED,
        SESSION_COMMITTED,
        SESSION_ABORTED
    }

    private Importer() {
    }

    public static Importer newInstance() {
        return new Importer();
    }

    public Optional<ImportLogger> getImportLogger() {
        return Optional.ofNullable(logger);
    }

    public Importer setImportLogger(ImportLogger logger) {
        this.logger = logger;
        return this;
    }

    public boolean isAutoCommit() {
        return autoCommit;
    }

    public Importer setAutoCommit(boolean autoCommit) {
        this.autoCommit = autoCommit;
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
        }

        Objects.requireNonNull(adapter, "The database adapter must not be null.");
        Objects.requireNonNull(options, "The import options must not be null.");

        try {
            referenceManager = ReferenceManager.newInstance(adapter, options);
            helpers = ConcurrentHashMap.newKeySet();
            service = ExecutorHelper.newFixedAndBlockingThreadPool(options.getNumberOfThreads() > 0 ?
                    options.getNumberOfThreads() :
                    Math.max(2, Runtime.getRuntime().availableProcessors()));

            countLatch = new CountLatch();
            contexts = ThreadLocal.withInitial(() -> {
                try {
                    ImportHelper helper = new ImportHelper(adapter, options, referenceManager, logger, autoCommit);
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
            throw new ImportException("Failed to start import session.", e);
        }
    }

    public CompletableFuture<FeatureDescriptor> importFeature(Feature feature) {
        CompletableFuture<FeatureDescriptor> result = new CompletableFuture<>();
        if (shouldRun) {
            if (state == State.SESSION_STARTED) {
                countLatch.increment();
                service.execute(() -> {
                    try {
                        result.complete(contexts.get().importFeature(feature));
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
        }

        return result;
    }

    public void commitSession() throws ImportException {
        if (state == State.SESSION_NOT_STARTED
                || state == State.SESSION_COMMITTED
                || state == State.SESSION_ABORTED) {
            return;
        }

        try {
            state = State.SESSION_COMMITTED;
            countLatch.await();

            for (ImportHelper helper : helpers) {
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

            try {
                referenceManager.resolveReferences();
            } finally {
                referenceManager.close();
            }
        } catch (Throwable e) {
            shouldRun = false;
            throw new ImportException("Failed to commit import session.", e);
        } finally {
            service.shutdown();
        }
    }

    public void abortSession() throws ImportException {
        if (state == State.SESSION_NOT_STARTED
                || state == State.SESSION_COMMITTED
                || state == State.SESSION_ABORTED) {
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
            service.shutdown();
        }
    }
}
