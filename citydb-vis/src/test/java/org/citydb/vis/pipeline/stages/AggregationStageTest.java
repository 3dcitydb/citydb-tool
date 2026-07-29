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
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Aggregation contract: after the stage, globalRoot has exactly one child
 * (the aggregation root), every cell root is still reachable as a descendant,
 * synthetic nodes are appended to {@code allNodes} with contiguous indices
 * (preserving the list-index-equals-node-index invariant that I3S node-page
 * emission depends on), and {@code meshNodeIndices} is untouched because
 * aggregation nodes never carry geometry.
 */
class AggregationStageTest {

    @Test
    void fourCellsCollapseUnderSingleAggregationRoot() throws VisExportException {
        Fixture fx = fixture(2, 2);
        new AggregationStage().execute(fx.ctx);

        SceneNode globalRoot = fx.ctx.allNodes().get(0);
        assertEquals(1, globalRoot.getChildren().size());

        // 4 cells fit MAX_CHILDREN_PER_AGG_NODE — one synthetic node only.
        SceneNode aggRoot = globalRoot.getChildren().get(0);
        assertEquals(fx.cellCount + 1, aggRoot.getIndex());
        assertEquals(fx.cellCount + 2, fx.ctx.allNodes().size());
        assertEquals(fx.cellRoots.size(), aggRoot.getChildren().size());
        for (SceneNode cellRoot : fx.cellRoots) {
            assertSame(aggRoot, cellRoot.getParent());
        }

        assertIndexInvariant(fx.ctx.allNodes());
        assertEquals(fx.meshIndicesBefore, fx.ctx.meshNodeIndices());
        assertNotNull(aggRoot.getBoundingVolume());
        assertNotNull(globalRoot.getBoundingVolume());
    }

    @Test
    void nineCellsProduceMultiLevelTreeWithoutLosingAnyCell() throws VisExportException {
        Fixture fx = fixture(3, 3);
        new AggregationStage().execute(fx.ctx);

        List<SceneNode> allNodes = fx.ctx.allNodes();
        SceneNode aggRoot = allNodes.get(0).getChildren().get(0);

        // 9 cells exceed the 4-child fanout — the aggregation must recurse,
        // so at least one child of the aggregation root is itself synthetic.
        assertTrue(allNodes.size() > fx.cellCount + 2,
                "expected more than one synthetic aggregation node");
        boolean hasSyntheticChild = aggRoot.getChildren().stream()
                .anyMatch(child -> child.getIndex() > fx.cellCount);
        assertTrue(hasSyntheticChild);

        // Every cell root is still reachable from the aggregation root, and
        // every synthetic node carries a bounding volume for LOD emission.
        Set<Integer> reachableCells = new HashSet<>();
        Deque<SceneNode> queue = new ArrayDeque<>(List.of(aggRoot));
        while (!queue.isEmpty()) {
            SceneNode node = queue.poll();
            if (node.getIndex() <= fx.cellCount) {
                reachableCells.add(node.getIndex());
            } else {
                assertNotNull(node.getBoundingVolume());
                queue.addAll(node.getChildren());
            }
        }
        assertEquals(fx.meshIndicesBefore, reachableCells);

        assertIndexInvariant(allNodes);
        assertEquals(fx.meshIndicesBefore, fx.ctx.meshNodeIndices());
    }

    @Test
    void singleCellIsStillWrappedInAggregationNode() throws VisExportException {
        Fixture fx = fixture(1, 1);
        new AggregationStage().execute(fx.ctx);

        SceneNode globalRoot = fx.ctx.allNodes().get(0);
        assertEquals(1, globalRoot.getChildren().size());
        SceneNode aggRoot = globalRoot.getChildren().get(0);
        assertEquals(2, aggRoot.getIndex());
        assertEquals(1, aggRoot.getChildren().size());
        assertSame(fx.cellRoots.get(0), aggRoot.getChildren().get(0));
        assertIndexInvariant(fx.ctx.allNodes());
    }

    private static void assertIndexInvariant(List<SceneNode> allNodes) {
        for (int i = 0; i < allNodes.size(); i++) {
            assertEquals(i, allNodes.get(i).getIndex(),
                    "allNodes.get(i).getIndex() must equal i");
        }
    }

    /** globalRoot plus gy×gx cell roots laid out on a unit-degree grid. */
    private static Fixture fixture(int rows, int cols) {
        SceneNode globalRoot = new SceneNode(0, 0);
        List<SceneNode> allNodes = new ArrayList<>(List.of(globalRoot));
        List<SceneNode> cellRoots = new ArrayList<>();
        Map<Integer, int[]> gridCoords = new HashMap<>();
        Set<Integer> meshIndices = new HashSet<>();

        int index = 1;
        for (int gy = 0; gy < rows; gy++) {
            for (int gx = 0; gx < cols; gx++) {
                SceneNode cellRoot = new SceneNode(index, 1);
                cellRoot.setBoundingVolume(BoundingVolume.ofBoundingBox(
                        gx, gy, 0.0, gx + 1.0, gy + 1.0, 10.0));
                globalRoot.addChild(cellRoot);
                allNodes.add(cellRoot);
                cellRoots.add(cellRoot);
                gridCoords.put(index, new int[]{gy, gx});
                meshIndices.add(index);
                index++;
            }
        }

        // The stage reads only allNodes, cellRootGridCoords and meshNodeIndices;
        // stores are never touched, so none are created here.
        PipelineContext ctx = new PipelineContext(null, new Tiles3DFormatOptions(),
                new AttributeEncoder(), cellRoots.size(), 2)
                .setAllNodes(allNodes)
                .setMeshNodeIndices(new HashSet<>(meshIndices))
                .setCellRootGridCoords(gridCoords);
        return new Fixture(ctx, cellRoots, cellRoots.size(), meshIndices);
    }

    private record Fixture(PipelineContext ctx, List<SceneNode> cellRoots, int cellCount,
                           Set<Integer> meshIndicesBefore) {
    }
}
