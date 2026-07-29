/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences (HFT Stuttgart) <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.pipeline.stages;

import org.citydb.vis.VisExportException;
import org.citydb.vis.attribute.AttributeEncoder;
import org.citydb.vis.options.Tiles3DFormatOptions;
import org.citydb.vis.pipeline.PipelineContext;
import org.citydb.vis.scene.SceneNode;
import org.citydb.vis.store.NodeEntry;
import org.citydb.vis.store.SpatialEntry;
import org.citydb.vis.store.VisExportStores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cell = leaf: each populated grid cell becomes exactly one mesh-carrying
 * scene node under the global root, its compact {@link NodeEntry} list is
 * flushed to the node entry store, and the partitioned store is consumed and
 * closed. The list-index-equals-node-index invariant established here is what
 * every later stage (mixed-node split, aggregation, atlas split) relies on.
 */
class TreeBuildingStageTest {
    private static final double[] EXTENT = {0.0, 0.0, 0.0, 0.02, 0.02, 10.0};

    @Test
    void buildsOneLeafPerPopulatedCell(@TempDir Path tempDir) throws IOException, VisExportException {
        try (VisExportStores stores = new VisExportStores(2, tempDir.resolve("stores"))) {
            // Three features in two grid cells: 1 and 2 share the SW cell,
            // 3 sits alone in the NE cell. gridDim = ceil(2226.4 / 1200) = 2.
            stores.getSpatialEntryStore().store(entry(1L, 0.004, 0.004), 0);
            stores.getSpatialEntryStore().store(entry(2L, 0.006, 0.006), 0);
            stores.getSpatialEntryStore().store(entry(3L, 0.015, 0.015), 1);

            Tiles3DFormatOptions opts = new Tiles3DFormatOptions();
            opts.setGridEdgeLength(1200.0);
            PipelineContext ctx = new PipelineContext(stores, opts, new AttributeEncoder(), 3, 2)
                    .setExtent(EXTENT);
            new PartitioningStage().execute(ctx);
            new TreeBuildingStage().execute(ctx);

            // Partitioned store is consumed and released.
            assertNull(ctx.partitioned());

            List<SceneNode> allNodes = ctx.allNodes();
            assertEquals(3, allNodes.size());

            // Index invariant: allNodes.get(i).getIndex() == i.
            for (int i = 0; i < allNodes.size(); i++) {
                assertEquals(i, allNodes.get(i).getIndex());
            }

            SceneNode globalRoot = allNodes.get(0);
            assertEquals(2, globalRoot.getChildren().size());
            for (SceneNode leaf : globalRoot.getChildren()) {
                assertSame(globalRoot, leaf.getParent());
            }

            assertEquals(Set.of(1, 2), ctx.meshNodeIndices());
            assertEquals(Set.of(1, 2), ctx.cellRootGridCoords().keySet());

            // Leaf entries: one leaf carries features {1, 2}, the other {3}.
            List<NodeEntry> leafA = stores.getNodeEntryStore().loadNode(1);
            List<NodeEntry> leafB = stores.getNodeEntryStore().loadNode(2);
            assertEquals(3, leafA.size() + leafB.size());
            List<NodeEntry> pair = leafA.size() == 2 ? leafA : leafB;
            List<NodeEntry> single = leafA.size() == 2 ? leafB : leafA;
            assertEquals(2, pair.size());
            assertEquals(1, single.size());
            assertEquals(3L, single.get(0).id());

            // Feature counts mirror the entry lists; leaf and root bounding
            // volumes exist (root = union of leaves via finalizeGlobalRoot).
            for (int i = 1; i < allNodes.size(); i++) {
                SceneNode leaf = allNodes.get(i);
                assertEquals(stores.getNodeEntryStore().loadNode(i).size(), leaf.getFeatureCount());
                assertTrue(leaf.getBoundingVolume() != null);
            }
            assertTrue(globalRoot.getBoundingVolume() != null);

            // No textures or baked colors were registered in this run.
            assertFalse(ctx.hasTextures());
            assertFalse(ctx.hasColors());
        }
    }

    @Test
    void singleCellDatasetYieldsRootPlusOneLeaf(@TempDir Path tempDir)
            throws IOException, VisExportException {
        try (VisExportStores stores = new VisExportStores(2, tempDir.resolve("stores"))) {
            stores.getSpatialEntryStore().store(entry(7L, 0.01, 0.01), 0);

            // gridEdgeLength 0 = auto sentinel: everything in one root cell.
            PipelineContext ctx = new PipelineContext(stores, new Tiles3DFormatOptions(),
                    new AttributeEncoder(), 1, 2).setExtent(EXTENT);
            new PartitioningStage().execute(ctx);
            new TreeBuildingStage().execute(ctx);

            assertEquals(2, ctx.allNodes().size());
            assertEquals(Set.of(1), ctx.meshNodeIndices());
            assertEquals(1, ctx.allNodes().get(0).getChildren().size());
            assertEquals(List.of(new NodeEntry(7L, 0L, 0L)),
                    stores.getNodeEntryStore().loadNode(1));
        }
    }

    private static SpatialEntry entry(long id, double cx, double cy) {
        return new SpatialEntry(id, cx, cy,
                new double[]{cx - 0.001, cy - 0.001, 0.0, cx + 0.001, cy + 0.001, 5.0}, 0L, 0L);
    }
}
