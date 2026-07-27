/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences (HFT Stuttgart) <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.store;

import org.citydb.model.common.Name;
import org.citydb.vis.geometry.TriangleMesh;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Sharded handle contract: handles encode (shardId, offset) and stay
 * non-negative — the sign bit is reserved as the {@link InstanceStore}
 * discriminator, so negative loads must be rejected rather than mis-read.
 * Concurrent stores across contended shards must yield handles that all load
 * back the right mesh, and {@code hasColors} must surface from any shard.
 */
class ShardedMeshStoreTest {
    private static final Name SURFACE = Name.of("WallSurface", "ns");

    @Test
    void shardCountOutsideValidRangeIsRejected(@TempDir Path tempDir) {
        assertThrows(IllegalArgumentException.class, () -> new ShardedMeshStore(0, tempDir));
        assertThrows(IllegalArgumentException.class,
                () -> new ShardedMeshStore(ShardedMeshStore.MAX_SHARDS + 1, tempDir));
    }

    @Test
    void negativeHandleIsRejectedAsInstanceHandle(@TempDir Path tempDir) throws IOException {
        try (ShardedMeshStore store = new ShardedMeshStore(2, tempDir)) {
            assertThrows(IllegalArgumentException.class, () -> store.load(-1L));
        }
    }

    @Test
    void meshesRoundTripAcrossShards(@TempDir Path tempDir) throws IOException {
        try (ShardedMeshStore store = new ShardedMeshStore(3, tempDir)) {
            // Hints 0..4 wrap over 3 shards, so at least one shard holds two
            // meshes at distinct offsets.
            List<Long> handles = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                handles.add(store.store(mesh(100L + i), i));
            }

            for (int i = 0; i < 5; i++) {
                long handle = handles.get(i);
                assertTrue(handle >= 0, "mesh handles must stay non-negative");
                TriangleMesh loaded = store.load(handle);
                assertEquals(List.of(100L + i), loaded.getFeatureIds());
            }
        }
    }

    @Test
    void concurrentStoresYieldIndependentlyLoadableHandles(@TempDir Path tempDir)
            throws Exception {
        int threads = 4;
        int perThread = 100;
        try (ShardedMeshStore store = new ShardedMeshStore(2, tempDir)) {
            Map<Long, Long> handleToFeature = new ConcurrentHashMap<>();
            CountDownLatch start = new CountDownLatch(1);
            List<Thread> workers = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                int threadId = t;
                Thread worker = new Thread(() -> {
                    try {
                        start.await();
                        for (int i = 0; i < perThread; i++) {
                            long featureId = threadId * 1000L + i;
                            // All threads share both shards — contended path.
                            long handle = store.store(mesh(featureId), i);
                            handleToFeature.put(handle, featureId);
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

            // Every handle is unique and loads back its own feature id.
            assertEquals(threads * perThread, handleToFeature.size());
            for (Map.Entry<Long, Long> e : handleToFeature.entrySet()) {
                assertEquals(List.of(e.getValue()), store.load(e.getKey()).getFeatureIds());
            }
        }
    }

    @Test
    void hasColorsSurfacesFromAnyShard(@TempDir Path tempDir) throws IOException {
        try (ShardedMeshStore store = new ShardedMeshStore(2, tempDir)) {
            store.store(mesh(1L), 0);
            assertFalse(store.hasColors());

            TriangleMesh colored = new TriangleMesh();
            int v0 = colored.addVertex(0.0, 0.0, 0.0, 0, 0, 1, 1f, 0f, 0f, 0.5f);
            int v1 = colored.addVertex(1.0, 0.0, 0.0, 0, 0, 1, 1f, 0f, 0f, 0.5f);
            int v2 = colored.addVertex(0.0, 1.0, 0.0, 0, 0, 1, 1f, 0f, 0f, 0.5f);
            colored.addTriangle(v0, v1, v2, 2L, -1, true, SURFACE);
            store.store(colored, 1);

            assertTrue(store.hasColors());
        }
    }

    /** Minimal single-triangle mesh tagged with the given feature id. */
    private static TriangleMesh mesh(long featureId) {
        TriangleMesh mesh = new TriangleMesh();
        int v0 = mesh.addVertex(0.0, 0.0, 0.0, 0, 0, 1);
        int v1 = mesh.addVertex(1.0, 0.0, 0.0, 0, 0, 1);
        int v2 = mesh.addVertex(0.0, 1.0, 0.0, 0, 0, 1);
        mesh.addTriangle(v0, v1, v2, featureId, -1, false, SURFACE);
        return mesh;
    }
}
