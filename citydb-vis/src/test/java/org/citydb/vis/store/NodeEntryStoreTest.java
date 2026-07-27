/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences (HFT Stuttgart) <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.store;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The node-index → entry-list mapping must round-trip, tolerate reads of
 * unknown or out-of-capacity indices (both legitimately happen: split stages
 * probe every mesh node, and orphaned intermediates are never re-read), and
 * grow transparently when split stages write nodes beyond the estimate that
 * sized the store.
 */
class NodeEntryStoreTest {

    @Test
    void writeThenLoadRoundTrips(@TempDir Path tempDir) throws IOException {
        try (NodeEntryStore store = new NodeEntryStore(tempDir, 4)) {
            List<NodeEntry> entries = List.of(
                    new NodeEntry(1L, 100L, 1000L),
                    new NodeEntry(2L, 200L, 2000L));
            store.writeNode(1, entries);
            store.writeNode(3, List.of(new NodeEntry(9L, 900L, 9000L)));

            assertEquals(entries, store.loadNode(1));
            assertEquals(List.of(new NodeEntry(9L, 900L, 9000L)), store.loadNode(3));
        }
    }

    @Test
    void unknownAndOutOfCapacityIndicesLoadEmpty(@TempDir Path tempDir) throws IOException {
        try (NodeEntryStore store = new NodeEntryStore(tempDir, 4)) {
            store.writeNode(1, List.of(new NodeEntry(1L, 0L, 0L)));

            assertTrue(store.loadNode(2).isEmpty());
            // Way beyond capacity — must return empty, not throw.
            assertTrue(store.loadNode(100).isEmpty());
        }
    }

    @Test
    void emptyEntryListIsANoOp(@TempDir Path tempDir) throws IOException {
        try (NodeEntryStore store = new NodeEntryStore(tempDir, 4)) {
            store.writeNode(1, List.of());
            assertTrue(store.loadNode(1).isEmpty());
        }
    }

    @Test
    void capacityGrowsWhenSplitStagesWriteBeyondTheEstimate(@TempDir Path tempDir)
            throws IOException {
        // Initial capacity 2 (node estimate), then a split stage appends
        // node index 10 — the index arrays must grow, and earlier nodes must
        // stay intact.
        try (NodeEntryStore store = new NodeEntryStore(tempDir, 2)) {
            store.writeNode(1, List.of(new NodeEntry(1L, 100L, 1000L)));
            store.writeNode(10, List.of(new NodeEntry(2L, 200L, 2000L)));

            assertEquals(List.of(new NodeEntry(1L, 100L, 1000L)), store.loadNode(1));
            assertEquals(List.of(new NodeEntry(2L, 200L, 2000L)), store.loadNode(10));
            assertTrue(store.loadNode(5).isEmpty());
        }
    }
}
