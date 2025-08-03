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

package org.citydb.operation.importer.reference;

import org.citydb.core.cache.PersistentMapStore;
import org.citydb.core.concurrent.CountLatch;
import org.citydb.database.adapter.DatabaseAdapter;
import org.citydb.operation.importer.ImportException;
import org.citydb.operation.importer.ImportOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ReferenceManager {
    private final Logger logger = LoggerFactory.getLogger(ReferenceManager.class);
    private final DatabaseAdapter adapter;
    private final int batchSize;

    private PersistentMapStore store;
    private ExecutorService service;
    private CountLatch countLatch;
    private Throwable exception;
    private volatile boolean shouldRun = true;

    private ReferenceManager(DatabaseAdapter adapter) {
        this.adapter = adapter;
        batchSize = Math.min(1000, adapter.getSchemaAdapter().getMaximumBatchSize());
    }

    public static ReferenceManager newInstance(DatabaseAdapter adapter, ImportOptions options) throws ImportException {
        try {
            return new ReferenceManager(adapter).initialize(options);
        } catch (Exception e) {
            throw new ImportException("Failed to create reference manager.", e);
        }
    }

    private ReferenceManager initialize(ImportOptions options) throws IOException {
        store = PersistentMapStore.builder()
                .tempDirectory(options.getTempDirectory())
                .build();
        logger.debug("Initialized cache for resolving references at {}.", store.getBackingFile());

        service = Executors.newFixedThreadPool(options.getNumberOfThreads() > 0 ?
                options.getNumberOfThreads() :
                Math.max(2, Runtime.getRuntime().availableProcessors()));
        countLatch = new CountLatch();

        return this;
    }

    public void storeReferences(ReferenceCache cache) {
        try {
            if (!cache.getTargets().isEmpty()) {
                store(cache.getTargets(), cache.getType().ordinal() + "t");
            }

            if (!cache.getReferences().isEmpty()) {
                store(cache.getReferences(), cache.getType().ordinal() + "r");
            }
        } finally {
            cache.clear();
        }
    }

    public void resolveReferences() {
        countLatch.await();
        for (CacheType type : CacheType.values()) {
            if (shouldRun
                    && store.hasMap(type.ordinal() + "r")
                    && store.hasMap(type.ordinal() + "t")) {
                store.withCurrentVersion(() -> {
                    Map<String, Long> targets = store.getOrCreateMap(type.ordinal() + "t");
                    Map<Long, String> references = store.getOrCreateMap(type.ordinal() + "r");
                    Map<Long, Long> resolved = new HashMap<>();

                    Iterator<Map.Entry<Long, String>> iterator = references.entrySet().iterator();
                    while (shouldRun && iterator.hasNext()) {
                        Map.Entry<Long, String> reference = iterator.next();
                        Long targetId = targets.get(reference.getValue());
                        if (targetId != null) {
                            resolved.put(reference.getKey(), targetId);
                            if (!iterator.hasNext() || resolved.size() == batchSize) {
                                update(resolved, type);
                                resolved.clear();
                            }
                        }
                    }
                });
            }
        }
    }

    private <K, V> void store(Map<K, V> unresolved, String name) {
        Map<K, V> values = new HashMap<>(unresolved);
        countLatch.increment();
        service.execute(() -> {
            try {
                store.getOrCreateMap(name).putAll(values);
            } finally {
                countLatch.decrement();
            }
        });
    }

    private void update(Map<Long, Long> resolved, CacheType type) {
        Map<Long, Long> values = new HashMap<>(resolved);
        countLatch.increment();
        service.execute(() -> {
            try {
                try (Connection connection = adapter.getPool().getConnection(true);
                     PreparedStatement stmt = connection.prepareStatement("update " +
                             adapter.getConnectionDetails().getSchema() + "." + type.getTable() +
                             " set " + type.getColumn() + " = ? where id = ?")) {
                    for (Map.Entry<Long, Long> entry : values.entrySet()) {
                        stmt.setLong(1, entry.getValue());
                        stmt.setLong(2, entry.getKey());
                        stmt.addBatch();
                    }

                    stmt.executeBatch();
                }
            } catch (Throwable e) {
                shouldRun = false;
                exception = e;
            } finally {
                countLatch.decrement();
            }
        });
    }

    public void close() throws ImportException {
        countLatch.await();
        service.shutdown();
        store.close();
        if (exception != null) {
            throw new ImportException("Failed to resolve references.", exception);
        }
    }
}
