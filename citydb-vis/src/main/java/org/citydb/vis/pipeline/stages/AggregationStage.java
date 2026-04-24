/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences (HFT Stuttgart) <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.pipeline.stages;

import org.citydb.vis.VisExportException;
import org.citydb.vis.pipeline.PipelineContext;
import org.citydb.vis.pipeline.Stage;
import org.citydb.vis.scene.CellAggregator;
import org.citydb.vis.scene.SceneNode;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Wraps the cell leaves under a 2×2 spatial aggregation tree so both output
 * formats get a multi-level LOD cascade instead of a flat globalRoot → cells
 * fanout.
 * <p>
 * Without this stage, both 3D Tiles and I3S runtimes refine from the root
 * directly to every populated grid cell the moment the root's screen-space
 * error threshold is crossed — loading the entire dataset at once. The
 * aggregation inserts intermediate nodes with their own bounding volumes
 * and geometric errors, giving the runtime progressive refinement targets
 * that scale with camera distance.
 * <p>
 * After this stage:
 * <ul>
 *   <li>{@code globalRoot} has a single child — the aggregation root.</li>
 *   <li>The aggregation subtree ends at the cell roots (the bounding
 *       volumes and feature content populated by earlier stages).</li>
 *   <li>All synthetic aggregation nodes are appended to
 *       {@link PipelineContext#allNodes()} so I3S node-page emission sees
 *       them. Their indices are contiguous starting at the previous
 *       {@code allNodes.size()}, preserving the list-index-equals-node-index
 *       invariant.</li>
 *   <li>{@link PipelineContext#meshNodeIndices()} is unchanged — aggregation
 *       nodes never carry geometry.</li>
 * </ul>
 * <p>
 * Run after {@link MixedNodeSplitStage}: mixed-texture cell roots gain
 * textured/untextured children during that stage, and aggregation wraps
 * the resulting cell subtrees as-is.
 */
public final class AggregationStage implements Stage {
    @Override
    public void execute(PipelineContext ctx) throws VisExportException {
        List<SceneNode> allNodes = ctx.allNodes();
        SceneNode globalRoot = allNodes.get(0);

        // Nothing to aggregate when the dataset has no cells. VisWriter.close
        // normally short-circuits on empty input before the pipeline runs,
        // but guarding keeps the stage safe if that contract ever changes.
        if (globalRoot.getChildren().isEmpty()) {
            return;
        }

        int firstSyntheticIndex = allNodes.size();
        AtomicInteger indexer = new AtomicInteger(firstSyntheticIndex);
        SceneNode aggRoot = CellAggregator.build(globalRoot.getChildren(),
                ctx.cellRootGridCoords(), indexer);

        // Walk the aggregation subtree and append the synthetic nodes (those
        // with index ≥ firstSyntheticIndex) to allNodes. Existing cell roots
        // and their descendants are already registered and must be skipped.
        collectSynthetic(aggRoot, allNodes, firstSyntheticIndex);

        // Re-parent: globalRoot now has exactly one child — the aggregation
        // root. CellAggregator already reassigned each cell root's parent
        // when it called addChild, so clearing globalRoot's list and linking
        // aggRoot leaves every cell root correctly attached in the new tree.
        globalRoot.getChildren().clear();
        globalRoot.addChild(aggRoot);
        globalRoot.updateBoundingVolume();
    }

    private static void collectSynthetic(SceneNode node, List<SceneNode> allNodes,
                                         int firstSyntheticIndex) {
        if (node.getIndex() >= firstSyntheticIndex) {
            allNodes.add(node);
        }
        for (SceneNode child : node.getChildren()) {
            collectSynthetic(child, allNodes, firstSyntheticIndex);
        }
    }
}
