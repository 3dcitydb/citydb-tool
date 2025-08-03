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

package org.citydb.cli.exporter.util;

import org.apache.logging.log4j.Logger;
import org.citydb.cli.logging.LoggerManager;
import org.citydb.io.writer.FeatureWriter;
import org.citydb.io.writer.WriteException;
import org.citydb.model.feature.Feature;
import org.citydb.model.feature.FeatureDescriptor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiConsumer;

public class SequentialWriter implements FeatureWriter {
    private final Logger logger = LoggerManager.getInstance().getLogger(SequentialWriter.class);
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition condition = lock.newCondition();
    private final Map<Long, CacheEntry> cache = new HashMap<>();
    private final FeatureWriter writer;

    private volatile boolean shouldRun = true;
    private long currentId = 1;

    private record CacheEntry(Feature feature, BiConsumer<Boolean, Throwable> onCompletion) {
    }

    private SequentialWriter(FeatureWriter writer) {
        this.writer = Objects.requireNonNull(writer, "The feature writer must not be null.");
    }

    public static SequentialWriter of(FeatureWriter writer) {
        return new SequentialWriter(writer);
    }

    @Override
    public void write(Feature feature, BiConsumer<Boolean, Throwable> onCompletion) throws WriteException {
        long sequenceId = feature.getDescriptor()
                .map(FeatureDescriptor::getSequenceId)
                .orElse(0L);
        if (sequenceId > 0) {
            lock.lock();
            try {
                if (currentId == sequenceId) {
                    writer.write(feature).whenComplete(onCompletion);
                    currentId++;

                    CacheEntry entry;
                    while ((entry = cache.remove(currentId)) != null) {
                        condition.signal();
                        writer.write(entry.feature).whenComplete(entry.onCompletion);
                        currentId++;
                    }
                } else {
                    cache.put(sequenceId, new CacheEntry(feature, onCompletion));
                    if (shouldRun) {
                        try {
                            condition.await();
                        } catch (InterruptedException e) {
                            throw new WriteException("Failed to write feature.", e);
                        }
                    }
                }
            } finally {
                lock.unlock();
            }
        } else {
            writer.write(feature).whenComplete(onCompletion);
        }
    }

    @Override
    public CompletableFuture<Boolean> write(Feature feature) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void cancel() {
        shouldRun = false;
        writer.cancel();
        lock.lock();
        try {
            condition.signalAll();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void close() throws WriteException {
        try {
            if (shouldRun && !cache.isEmpty()) {
                logger.error("Sequential writer cache is not empty. Writing remaining objects...");
                List<CacheEntry> entries = cache.entrySet().stream()
                        .sorted(Map.Entry.comparingByKey())
                        .map(Map.Entry::getValue)
                        .toList();
                for (CacheEntry entry : entries) {
                    writer.write(entry.feature).whenComplete(entry.onCompletion);
                }
            }

            writer.close();
        } finally {
            cache.clear();
        }
    }
}
