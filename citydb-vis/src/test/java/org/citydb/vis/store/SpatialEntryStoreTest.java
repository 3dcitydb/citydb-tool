/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences (HFT Stuttgart) <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.store;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the write-phase/close-phase handoff of the sharded spatial store:
 * concurrent appends from writer threads must all survive into the
 * sequential close-phase iteration, the 88-byte record must round-trip every
 * field, and iteration must cross the 4096-entry chunk boundary without
 * dropping or duplicating records.
 */
class SpatialEntryStoreTest {

    @Test
    void recordRoundTripPreservesAllFields(@TempDir Path tempDir) throws IOException {
        try (SpatialEntryStore store = new SpatialEntryStore(2, tempDir)) {
            double[] bbox = {8.1, 48.7, -2.5, 8.2, 48.8, 30.0};
            store.store(new SpatialEntry(42L, 8.15, 48.75, bbox, 123456789L, 987654321L), 0);

            Iterator<SpatialEntry> it = store.iterator();
            assertTrue(it.hasNext());
            SpatialEntry loaded = it.next();
            assertEquals(42L, loaded.id());
            assertEquals(8.15, loaded.centerX());
            assertEquals(48.75, loaded.centerY());
            assertArrayEquals(bbox, loaded.bbox());
            assertEquals(123456789L, loaded.meshHandle());
            assertEquals(987654321L, loaded.attrOffset());
            assertTrue(!it.hasNext());
        }
    }

    @Test
    void iterationCrossesChunkBoundary(@TempDir Path tempDir) throws IOException {
        // The iterator reads 4096-entry chunks; 5000 entries in a single
        // shard forces a chunk rollover mid-shard.
        int total = 5000;
        try (SpatialEntryStore store = new SpatialEntryStore(1, tempDir)) {
            for (int i = 0; i < total; i++) {
                store.store(entry(i), 0);
            }

            assertEquals(total, store.entryCount());
            Set<Long> seen = collectIds(store);
            assertEquals(total, seen.size());
        }
    }

    @Test
    void concurrentWritesAllSurviveIntoIteration(@TempDir Path tempDir) throws Exception {
        int threads = 4;
        int perThread = 500;
        try (SpatialEntryStore store = new SpatialEntryStore(threads, tempDir)) {
            CountDownLatch start = new CountDownLatch(1);
            List<Thread> workers = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                int threadId = t;
                Thread worker = new Thread(() -> {
                    try {
                        start.await();
                        for (int i = 0; i < perThread; i++) {
                            // Disjoint id ranges per thread; every thread also
                            // hits every shard so per-shard write positions
                            // are contended, not just per-thread files.
                            store.store(entry(threadId * perThread + i), i);
                        }
                    } catch (IOException | InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                });
                workers.add(worker);
                worker.start();
            }
            start.countDown();
            for (Thread worker : workers) {
                worker.join();
            }

            assertEquals((long) threads * perThread, store.entryCount());
            Set<Long> seen = collectIds(store);
            assertEquals(threads * perThread, seen.size());
        }
    }

    private static SpatialEntry entry(long id) {
        return new SpatialEntry(id, id * 0.001, id * 0.002,
                new double[]{0.0, 0.0, 0.0, 1.0, 1.0, 1.0}, id * 10, id * 20);
    }

    private static Set<Long> collectIds(SpatialEntryStore store) {
        Set<Long> seen = new HashSet<>();
        Iterator<SpatialEntry> it = store.iterator();
        while (it.hasNext()) {
            assertTrue(seen.add(it.next().id()), "duplicate entry surfaced by iterator");
        }
        return seen;
    }
}
