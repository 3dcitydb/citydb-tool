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

package org.citydb.operation.exporter;

import org.citydb.core.concurrent.CountLatch;
import org.citydb.core.concurrent.ExecutorHelper;
import org.citydb.core.function.CheckedSupplier;
import org.citydb.database.adapter.DatabaseAdapter;
import org.citydb.model.feature.Feature;
import org.citydb.model.geometry.ImplicitGeometry;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

public class Exporter {
    private ExecutorService service;
    private ThreadLocal<ExportHelper> contexts;
    private Set<ExportHelper> helpers;
    private CountLatch countLatch;

    private volatile State state = State.SESSION_NOT_STARTED;
    private volatile boolean shouldRun;

    public enum State {
        SESSION_NOT_STARTED,
        SESSION_STARTED,
        SESSION_CLOSED
    }

    private Exporter() {
    }

    public static Exporter newInstance() {
        return new Exporter();
    }

    public State getState() {
        return state;
    }

    public boolean wasSuccessful() {
        return shouldRun;
    }

    public Exporter startSession(DatabaseAdapter adapter, ExportOptions options) throws ExportException {
        if (state == State.SESSION_STARTED) {
            return this;
        }

        Objects.requireNonNull(adapter, "The database adapter must not be null.");
        Objects.requireNonNull(options, "The export options must not be null.");

        try {
            helpers = ConcurrentHashMap.newKeySet();
            service = ExecutorHelper.newFixedAndBlockingThreadPool(options.getNumberOfThreads() > 0 ?
                    options.getNumberOfThreads() :
                    Math.max(2, Runtime.getRuntime().availableProcessors()),
                    1000);

            countLatch = new CountLatch();
            contexts = ThreadLocal.withInitial(() -> {
                try {
                    ExportHelper helper = new ExportHelper(adapter, options);
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
            throw new ExportException("Failed to start export session.", e);
        }
    }

    public CompletableFuture<Feature> exportFeature(long id) {
        return exportFeature(id, 0);
    }

    public CompletableFuture<Feature> exportFeature(long id, long sequenceId) {
        return doExport(() -> contexts.get().exportFeature(id, sequenceId));
    }

    public CompletableFuture<ImplicitGeometry> exportImplicitGeometry(long id) {
        return doExport(() -> contexts.get().exportImplicitGeometry(id));
    }

    private <T> CompletableFuture<T> doExport(CheckedSupplier<T, Throwable> supplier) {
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
                result.completeExceptionally(new ExportException("Illegal to export data outside a session."));
            }
        }

        return result;
    }

    public void closeSession() throws ExportException {
        if (state == State.SESSION_NOT_STARTED || state == State.SESSION_CLOSED) {
            return;
        }

        try {
            state = State.SESSION_CLOSED;
            countLatch.await();

            for (ExportHelper helper : helpers) {
                helper.close();
            }
        } catch (Exception e) {
            shouldRun = false;
            throw new ExportException("Failed to close export session.", e);
        } finally {
            service.shutdown();
        }
    }
}
