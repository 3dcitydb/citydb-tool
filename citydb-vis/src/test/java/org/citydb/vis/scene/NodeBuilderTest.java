/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences (HFT Stuttgart) <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.scene;

import org.citydb.vis.store.NodeEntry;
import org.citydb.vis.store.SpatialEntry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Cell-leaf compaction contract: the leaf's bounding volume is the exact
 * union of its entries' bboxes, and each 88-byte {@link SpatialEntry} is
 * compacted to a 24-byte {@link NodeEntry} preserving id, meshHandle and
 * attrOffset in input order — the output phase later joins mesh and
 * attribute data back through exactly these two fields.
 */
class NodeBuilderTest {

    @Test
    void cellLeafBboxIsUnionAndEntriesCompactInOrder() {
        List<SpatialEntry> entries = List.of(
                new SpatialEntry(1L, 8.15, 48.75,
                        new double[]{8.1, 48.7, 10.0, 8.2, 48.8, 25.0}, 100L, 1000L),
                new SpatialEntry(2L, 8.45, 48.65,
                        new double[]{8.4, 48.6, -5.0, 8.5, 48.7, 12.0}, 200L, 2000L));

        NodeBuilder.CellLeaf leaf = NodeBuilder.buildCellLeaf(entries);

        assertEquals(2, leaf.node().getFeatureCount());
        assertEquals(List.of(
                new NodeEntry(1L, 100L, 1000L),
                new NodeEntry(2L, 200L, 2000L)), leaf.entries());

        BoundingVolume bbox = leaf.node().getBoundingVolume();
        assertEquals(8.1, bbox.getMinX());
        assertEquals(48.6, bbox.getMinY());
        assertEquals(-5.0, bbox.getMinZ());
        assertEquals(8.5, bbox.getMaxX());
        assertEquals(48.8, bbox.getMaxY());
        assertEquals(25.0, bbox.getMaxZ());
    }

    @Test
    void finalizeGlobalRootMergesChildBoundingVolumes() {
        SceneNode root = new SceneNode(0, 0);
        SceneNode childA = new SceneNode(1, 1);
        childA.setBoundingVolume(BoundingVolume.ofBoundingBox(0.0, 0.0, 0.0, 1.0, 1.0, 10.0));
        SceneNode childB = new SceneNode(2, 1);
        childB.setBoundingVolume(BoundingVolume.ofBoundingBox(2.0, 1.0, -3.0, 4.0, 5.0, 6.0));
        root.addChild(childA);
        root.addChild(childB);

        NodeBuilder.finalizeGlobalRoot(root);

        BoundingVolume bbox = root.getBoundingVolume();
        assertEquals(0.0, bbox.getMinX());
        assertEquals(0.0, bbox.getMinY());
        assertEquals(-3.0, bbox.getMinZ());
        assertEquals(4.0, bbox.getMaxX());
        assertEquals(5.0, bbox.getMaxY());
        assertEquals(10.0, bbox.getMaxZ());
    }
}
