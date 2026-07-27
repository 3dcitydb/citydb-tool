/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences (HFT Stuttgart) <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.store;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the length-prefixed binary attribute format: the three typed value
 * lanes (Long/Double/String) round-trip including non-ASCII UTF-8, values of
 * unsupported runtime types degrade to the null lane (key dropped on read,
 * not corrupted), null strings normalize to empty, and offsets returned by
 * interleaved stores address their own record independently.
 */
class AttributeStoreTest {

    @Test
    void typedAttributesRoundTrip(@TempDir Path tempDir) throws IOException {
        try (AttributeStore store = new AttributeStore(tempDir)) {
            Map<String, Object> attributes = new LinkedHashMap<>();
            attributes.put("storeysAboveGround", 5L);
            attributes.put("measuredHeight", 17.35);
            attributes.put("name", "Hauptbahnhof 中央车站");
            // Unsupported runtime type — serialized as the null lane.
            attributes.put("flag", Boolean.TRUE);
            attributes.put("missing", null);

            long offset = store.store("BLDG_0001", "Building", attributes);
            AttributeStore.FeatureAttrs loaded = store.load(offset);

            assertEquals("BLDG_0001", loaded.objectId());
            assertEquals("Building", loaded.featureType());
            assertEquals(5L, loaded.attributes().get("storeysAboveGround"));
            assertEquals(17.35, loaded.attributes().get("measuredHeight"));
            assertEquals("Hauptbahnhof 中央车站", loaded.attributes().get("name"));
            // Null-lane keys are skipped on deserialization, not resurrected.
            assertFalse(loaded.attributes().containsKey("flag"));
            assertFalse(loaded.attributes().containsKey("missing"));
            assertEquals(3, loaded.attributes().size());
        }
    }

    @Test
    void interleavedStoresLoadIndependentlyByOffset(@TempDir Path tempDir) throws IOException {
        try (AttributeStore store = new AttributeStore(tempDir)) {
            long first = store.store("A", "Building", Map.of("k", 1L));
            long second = store.store("B", "Bridge", Map.of("k", 2L));

            // Read back in reverse order — offsets are absolute positions.
            assertEquals("B", store.load(second).objectId());
            assertEquals("A", store.load(first).objectId());
            assertEquals(2L, store.load(second).attributes().get("k"));
            assertEquals(1L, store.load(first).attributes().get("k"));
        }
    }

    @Test
    void nullStringsNormalizeToEmpty(@TempDir Path tempDir) throws IOException {
        try (AttributeStore store = new AttributeStore(tempDir)) {
            long offset = store.store(null, null, Map.of());
            AttributeStore.FeatureAttrs loaded = store.load(offset);

            assertEquals("", loaded.objectId());
            assertEquals("", loaded.featureType());
            assertTrue(loaded.attributes().isEmpty());
        }
    }
}
