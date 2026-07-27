/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences (HFT Stuttgart) <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.store;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The negative-pseudo-handle codec is the discriminator between instance
 * records and sharded mesh handles throughout the pipeline — record index 0
 * must map to a strictly negative handle and back, and no mesh handle
 * (non-negative by {@link ShardedMeshStore} contract) may classify as an
 * instance. The 72-byte record must round-trip every placement field.
 */
class InstanceStoreTest {

    @Test
    void handleCodecRoundTripsAndStaysNegative() {
        for (long index : new long[]{0L, 1L, 42L, Integer.MAX_VALUE}) {
            long handle = InstanceStore.toHandle(index);
            assertTrue(handle < 0, "instance handles must be negative");
            assertTrue(InstanceStore.isInstanceHandle(handle));
            assertEquals(index, InstanceStore.toIndex(handle));
        }
        assertFalse(InstanceStore.isInstanceHandle(0L));
        assertFalse(InstanceStore.isInstanceHandle(Long.MAX_VALUE));
    }

    @Test
    void recordRoundTripPreservesAllFields(@TempDir Path tempDir) throws IOException {
        try (InstanceStore store = new InstanceStore(tempDir)) {
            InstanceStore.InstanceRecord first = new InstanceStore.InstanceRecord(7,
                    8.15, 48.75, 123.5,
                    new float[]{0.0f, 0.0f, 0.7071f, 0.7071f},
                    new float[]{1.0f, 2.0f, 0.5f},
                    new float[]{0.8f, 0.2f, 0.1f, 1.0f});
            InstanceStore.InstanceRecord second = new InstanceStore.InstanceRecord(9,
                    -122.4, 37.8, -3.25,
                    new float[]{0.0f, 0.0f, 0.0f, 1.0f},
                    new float[]{1.0f, 1.0f, 1.0f},
                    new float[]{0.0f, 0.0f, 0.0f, 0.5f});

            assertEquals(0L, store.store(first));
            assertEquals(1L, store.store(second));

            InstanceStore.InstanceRecord loaded = store.load(0L);
            assertEquals(7, loaded.prototypeId());
            assertEquals(8.15, loaded.anchorX());
            assertEquals(48.75, loaded.anchorY());
            assertEquals(123.5, loaded.anchorZ());
            assertArrayEquals(first.rotation(), loaded.rotation());
            assertArrayEquals(first.scale(), loaded.scale());
            assertArrayEquals(first.styleColor(), loaded.styleColor());

            // Positional reads: the second record is independently addressable.
            assertEquals(9, store.load(1L).prototypeId());
        }
    }
}
