/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences (HFT Stuttgart) <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.pipeline.stages;

import org.citydb.vis.VisExportException;
import org.citydb.vis.scene.NodeBuilder;
import org.citydb.vis.pipeline.PipelineContext;
import org.citydb.vis.pipeline.Stage;
import org.citydb.vis.scene.SceneNode;
import org.citydb.vis.store.NodeEntry;
import org.citydb.vis.store.NodeEntryStore;
import org.citydb.vis.store.PartitionedEntryStore;
import org.citydb.vis.store.SpatialEntry;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Fused per-cell quadtree build + global merge + flush. Cells are processed
 * one at a time: load spatial entries from the partitioned store, build a
 * local quadtree, remap indices into the global tree, flush the compact
 * {@link NodeEntry} lists to {@link NodeEntryStore}, release cell data.
 * Peak heap is one cell's spatial-entry list.
 * <p>
 * Populates {@link PipelineContext#allNodes()} and
 * {@link PipelineContext#meshNodeIndices()}; closes
 * {@link PipelineContext#partitioned()} after consumption.
 */
public final class TreeBuildingStage implements Stage {
    @Override
    public void execute(PipelineContext ctx) throws VisExportException {
        try {
            int estimatedNodes = (int) Math.min(
                    (ctx.totalFeatures() / ctx.formatOptions().getMaxFeaturesPerNode()) * 3 + 1,
                    Integer.MAX_VALUE);
            NodeEntryStore nodeEntryStore = ctx.stores().initNodeEntryStore(estimatedNodes);

            List<SceneNode> allNodes = new ArrayList<>();
            Set<Integer> meshNodeIndices = new HashSet<>();
            Map<Integer, int[]> cellRootGridCoords = new HashMap<>();
            SceneNode globalRoot = new SceneNode(0, 0);
            allNodes.add(globalRoot);
            int nextIndex = 1;
            int gridDim = ctx.partitioned().gridDim();

            for (long cellKey : ctx.partitioned().cellKeys()) {
                List<SpatialEntry> cellEntries = ctx.partitioned().loadCell(cellKey);
                double[] cellExtent = NodeBuilder.computeExtent(cellEntries);
                NodeBuilder.CellTree cellTree =
                        NodeBuilder.buildCellTree(cellEntries, cellExtent,
                                ctx.formatOptions().getMaxFeaturesPerNode());

                int offset = nextIndex;
                for (SceneNode node : cellTree.nodes()) {
                    int oldIndex = node.getIndex();
                    int newIndex = oldIndex + offset;

                    List<NodeEntry> entries = cellTree.nodeEntryMap().get(oldIndex);
                    if (entries != null) {
                        nodeEntryStore.writeNode(newIndex, entries);
                        meshNodeIndices.add(newIndex);
                    }

                    node.setIndex(newIndex);
                    allNodes.add(node);
                }

                SceneNode cellRoot = cellTree.nodes().get(0);
                globalRoot.addChild(cellRoot);
                cellRootGridCoords.put(cellRoot.getIndex(),
                        PartitionedEntryStore.decodeKey(cellKey, gridDim));
                nextIndex += cellTree.nodes().size();
            }

            ctx.partitioned().close();
            ctx.setPartitioned(null);

            NodeBuilder.finalizeGlobalRoot(globalRoot);

            ctx.setAllNodes(allNodes);
            ctx.setMeshNodeIndices(meshNodeIndices);
            ctx.setCellRootGridCoords(cellRootGridCoords);
            ctx.setHasTextures(ctx.stores().getTextureStore().hasTextures());
        } catch (IOException e) {
            throw new VisExportException("Failed to build spatial tree.", e);
        }
    }
}
