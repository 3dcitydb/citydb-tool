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

package org.citydb.cli.importer.duplicate;

import org.apache.logging.log4j.Logger;
import org.citydb.cli.ExecutionException;
import org.citydb.cli.importer.ImportMode;
import org.citydb.cli.importer.ImportOptions;
import org.citydb.cli.importer.filter.Filter;
import org.citydb.cli.logging.LoggerManager;
import org.citydb.core.cache.PersistentMapStore;
import org.citydb.core.concurrent.CountLatch;
import org.citydb.core.concurrent.ExecutorHelper;
import org.citydb.database.adapter.DatabaseAdapter;
import org.citydb.io.reader.FeatureReader;
import org.citydb.model.feature.Feature;

import java.io.IOException;
import java.util.Deque;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

public class DuplicateController implements AutoCloseable {
    private final Logger logger = LoggerManager.getInstance().getLogger(DuplicateController.class);
    private final ImportOptions options;
    private final DatabaseAdapter adapter;
    private final boolean preview;
    private PersistentMapStore store;
    private Exception exception;

    public enum Result {
        ACCEPT_FILE,
        SKIP_FILE
    }

    private record Count(int features, int duplicatesInFile, int duplicatesInDatabase) {
    }

    private DuplicateController(ImportOptions options, DatabaseAdapter adapter, boolean preview) {
        this.options = Objects.requireNonNull(options, "The import options must not be null.");
        this.adapter = Objects.requireNonNull(adapter, "The database adapter must not be null.");
        this.preview = preview;
    }

    public static DuplicateController of(ImportOptions options, DatabaseAdapter adapter, boolean preview) {
        return new DuplicateController(options, adapter, preview);
    }

    public Result processDuplicates(FeatureReader reader, Filter filter) throws ExecutionException {
        Count count = findDuplicates(reader, filter);
        if (count.duplicatesInFile > 0) {
            if (options.getMode() == ImportMode.SKIP_EXISTING) {
                if (count.duplicatesInFile == count.features) {
                    return Result.SKIP_FILE;
                } else {
                    logger.info("Skipping {} duplicates from input file.", count.duplicatesInFile());
                }
            } else if (options.getMode() == ImportMode.DELETE_EXISTING
                    || options.getMode() == ImportMode.TERMINATE_EXISTING) {
                logger.info("Deleting {} duplicates from database{}...", count.duplicatesInDatabase,
                        preview ? " in preview mode" : "");
                DuplicateDeleter deleter = new DuplicateDeleter(options, adapter, preview);
                deleter.deleteDuplicates(store.getOrCreateMap("database-ids"));
                logger.info("Successfully deleted duplicates. Importing features...");
            }
        }

        return Result.ACCEPT_FILE;
    }

    public boolean isDuplicate(Feature feature) {
        if (store != null) {
            String objectId = feature.getObjectId().orElse(null);
            return objectId != null && store.getOrCreateMap("object-ids").containsKey(objectId);
        } else {
            return false;
        }
    }

    private Count findDuplicates(FeatureReader reader, Filter filter) throws ExecutionException {
        PersistentMapStore store = createOrResetStore();
        Map<String, Boolean> objectIds = store.getOrCreateMap("object-ids");
        Map<Long, Boolean> databaseIds = store.getOrCreateMap("database-ids");

        Deque<DuplicateFinder> finders = new ConcurrentLinkedDeque<>();
        ExecutorService service = ExecutorHelper.newFixedAndBlockingThreadPool(options.getNumberOfThreads() > 0 ?
                options.getNumberOfThreads() :
                Math.max(2, Runtime.getRuntime().availableProcessors()));

        CountLatch countLatch = new CountLatch();
        ThreadLocal<DuplicateFinder> contexts = ThreadLocal.withInitial(() -> {
            try {
                DuplicateFinder finder = new DuplicateFinder(objectIds, databaseIds, adapter);
                finders.add(finder);
                return finder;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        AtomicInteger counter = new AtomicInteger();
        try {
            filter.saveState();
            reader.read(feature -> {
                countLatch.increment();
                service.execute(() -> {
                    try {
                        contexts.get().process(feature);
                        counter.incrementAndGet();
                    } catch (Exception e) {
                        exception = e;
                        reader.cancel();
                    } finally {
                        countLatch.decrement();
                    }
                });
            });

            countLatch.await();
            if (exception != null) {
                throw exception;
            } else if (!finders.isEmpty()) {
                DuplicateFinder finder = finders.pop();
                for (DuplicateFinder other : finders) {
                    finder.process(other);
                    other.close();
                }

                finder.close();
            }
        } catch (Exception e) {
            throw new ExecutionException("Failed to check input file for duplicate features.", e);
        } finally {
            service.shutdown();
            filter.restoreState();
        }

        return new Count(counter.get(), objectIds.size(), databaseIds.size());
    }

    private PersistentMapStore createOrResetStore() throws ExecutionException {
        if (store == null) {
            try {
                store = PersistentMapStore.builder()
                        .tempDirectory(options.getTempDirectory().orElse(null))
                        .build();
                logger.debug("Initialized duplicate feature cache at {}.", store.getBackingFile());
            } catch (IOException e) {
                throw new ExecutionException("Failed to initialize local cache.", e);
            }
        } else {
            store.removeMap("object-ids");
            store.removeMap("database-ids");
        }

        return store;
    }

    @Override
    public void close() {
        exception = null;
        if (store != null) {
            store.close();
        }
    }
}
