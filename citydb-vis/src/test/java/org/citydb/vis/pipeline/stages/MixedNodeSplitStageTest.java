/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences (HFT Stuttgart) <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.pipeline.stages;

import org.citydb.vis.VisExportException;
import org.citydb.vis.attribute.AttributeEncoder;
import org.citydb.vis.options.Tiles3DFormatOptions;
import org.citydb.vis.pipeline.PipelineContext;
import org.citydb.vis.scene.BoundingVolume;
import org.citydb.vis.scene.SceneNode;
import org.citydb.vis.store.NodeEntry;
import org.citydb.vis.store.VisExportStores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Push-down split contract: a mesh node holding both textured and untextured
 * features becomes a content-less intermediate with exactly two homogeneous
 * children, while homogeneous and empty nodes pass through untouched. The
 * split must preserve the original's bounding volume on both children (it is
 * a reclassification, not a spatial subdivision) and keep the cell root in
 * place so grid coordinates stay valid for the aggregation stage.
 */
class MixedNodeSplitStageTest {
    private static final BoundingVolume BBOX =
            BoundingVolume.ofBoundingBox(8.1, 48.7, 0.0, 8.2, 48.8, 30.0);

    @Test
    void mixedNodeSplitsIntoTexturedAndUntexturedChildren(@TempDir Path tempDir)
            throws IOException, VisExportException {
        try (VisExportStores stores = new VisExportStores(2, tempDir.resolve("stores"))) {
            stores.initNodeEntryStore(4);
            stores.getNodeEntryStore().writeNode(1, List.of(
                    new NodeEntry(1L, 10L, 0L),
                    new NodeEntry(2L, 20L, 0L),
                    new NodeEntry(3L, 30L, 0L)));
            stores.setFeatureTextured(1L);
            stores.setFeatureTextured(3L);

            PipelineContext ctx = context(stores);
            new MixedNodeSplitStage().execute(ctx);

            List<SceneNode> allNodes = ctx.allNodes();
            assertEquals(4, allNodes.size());
            for (int i = 0; i < allNodes.size(); i++) {
                assertEquals(i, allNodes.get(i).getIndex());
            }

            // Original node keeps its place and bbox but loses mesh status.
            SceneNode original = allNodes.get(1);
            assertEquals(2, original.getChildren().size());
            assertEquals(Set.of(2, 3), ctx.meshNodeIndices());

            SceneNode texturedChild = allNodes.get(2);
            SceneNode untexturedChild = allNodes.get(3);
            assertSame(original, texturedChild.getParent());
            assertSame(original, untexturedChild.getParent());
            assertSame(BBOX, texturedChild.getBoundingVolume());
            assertSame(BBOX, untexturedChild.getBoundingVolume());
            assertEquals(original.getLevel() + 1, texturedChild.getLevel());
            assertEquals(original.getLevel() + 1, untexturedChild.getLevel());

            // Textured child carries features 1 and 3, untextured carries 2.
            List<NodeEntry> texturedEntries = stores.getNodeEntryStore().loadNode(2);
            List<NodeEntry> untexturedEntries = stores.getNodeEntryStore().loadNode(3);
            assertEquals(List.of(1L, 3L), texturedEntries.stream().map(NodeEntry::id).toList());
            assertEquals(List.of(2L), untexturedEntries.stream().map(NodeEntry::id).toList());
            assertEquals(2, texturedChild.getFeatureCount());
            assertEquals(1, untexturedChild.getFeatureCount());
        }
    }

    @Test
    void homogeneousNodesPassThroughUntouched(@TempDir Path tempDir)
            throws IOException, VisExportException {
        try (VisExportStores stores = new VisExportStores(2, tempDir.resolve("stores"))) {
            stores.initNodeEntryStore(2);
            // All features untextured — nothing to split.
            stores.getNodeEntryStore().writeNode(1, List.of(
                    new NodeEntry(1L, 10L, 0L),
                    new NodeEntry(2L, 20L, 0L)));

            PipelineContext ctx = context(stores);
            new MixedNodeSplitStage().execute(ctx);

            assertEquals(2, ctx.allNodes().size());
            assertEquals(Set.of(1), ctx.meshNodeIndices());
            assertTrue(ctx.allNodes().get(1).getChildren().isEmpty());
        }
    }

    @Test
    void emptyNodeIsSkipped(@TempDir Path tempDir) throws IOException, VisExportException {
        try (VisExportStores stores = new VisExportStores(2, tempDir.resolve("stores"))) {
            stores.initNodeEntryStore(2);
            // Node 1 never written — loadNode returns an empty list.

            PipelineContext ctx = context(stores);
            new MixedNodeSplitStage().execute(ctx);

            assertEquals(Set.of(1), ctx.meshNodeIndices());
            assertEquals(2, ctx.allNodes().size());
        }
    }

    /** Root (index 0) plus one mesh-carrying cell root (index 1). */
    private static PipelineContext context(VisExportStores stores) {
        SceneNode globalRoot = new SceneNode(0, 0);
        SceneNode cellRoot = new SceneNode(1, 1);
        cellRoot.setBoundingVolume(BBOX);
        globalRoot.addChild(cellRoot);

        List<SceneNode> allNodes = new ArrayList<>(List.of(globalRoot, cellRoot));
        return new PipelineContext(stores, new Tiles3DFormatOptions(), new AttributeEncoder(), 3, 2)
                .setAllNodes(allNodes)
                .setMeshNodeIndices(new HashSet<>(Set.of(1)));
    }
}
