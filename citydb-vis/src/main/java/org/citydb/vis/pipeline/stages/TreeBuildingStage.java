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
 * Per-cell leaf build + global merge + flush. Cells are processed one at
 * a time: load spatial entries from the partitioned store, compact them
 * into a single leaf {@link SceneNode}, remap its index into the global
 * tree, flush the compact {@link NodeEntry} list to {@link NodeEntryStore},
 * release cell data. Peak heap is one cell's spatial-entry list.
 * <p>
 * Populates {@link PipelineContext#allNodes()},
 * {@link PipelineContext#meshNodeIndices()},
 * {@link PipelineContext#cellRootGridCoords()},
 * {@link PipelineContext#hasTextures()} and
 * {@link PipelineContext#hasColors()}; closes
 * {@link PipelineContext#partitioned()} after consumption.
 */
public final class TreeBuildingStage implements Stage {
    @Override
    public void execute(PipelineContext ctx) throws VisExportException {
        try {
            int gridDim = ctx.partitioned().gridDim();
            // Each populated cell contributes one leaf plus the global root.
            // totalFeatures is a tight upper bound on populated cells (every
            // cell has ≥1 feature); gridDim² would overflow int for very
            // small edge lengths on country-scale extents, and overallocates
            // for sparse datasets.
            long gridCells = (long) gridDim * gridDim;
            int estimatedNodes = (int) Math.min(ctx.totalFeatures(), gridCells) + 1;
            NodeEntryStore nodeEntryStore = ctx.stores().initNodeEntryStore(estimatedNodes);

            List<SceneNode> allNodes = new ArrayList<>();
            Set<Integer> meshNodeIndices = new HashSet<>();
            Map<Integer, int[]> cellRootGridCoords = new HashMap<>();
            SceneNode globalRoot = new SceneNode(0, 0);
            allNodes.add(globalRoot);
            int nextIndex = 1;

            for (long cellKey : ctx.partitioned().cellKeys()) {
                List<SpatialEntry> cellEntries = ctx.partitioned().loadCell(cellKey);
                NodeBuilder.CellLeaf leaf = NodeBuilder.buildCellLeaf(cellEntries);

                SceneNode node = leaf.node();
                node.setIndex(nextIndex);
                nodeEntryStore.writeNode(nextIndex, leaf.entries());
                meshNodeIndices.add(nextIndex);
                allNodes.add(node);
                globalRoot.addChild(node);
                cellRootGridCoords.put(nextIndex,
                        PartitionedEntryStore.decodeKey(cellKey, gridDim));
                nextIndex++;
            }

            ctx.partitioned().close();
            ctx.setPartitioned(null);

            NodeBuilder.finalizeGlobalRoot(globalRoot);

            ctx.setAllNodes(allNodes);
            ctx.setMeshNodeIndices(meshNodeIndices);
            ctx.setCellRootGridCoords(cellRootGridCoords);
            ctx.setHasTextures(ctx.stores().getTextureStore().hasTextures());
            ctx.setHasColors(ctx.stores().getMeshStore().hasColors());
        } catch (IOException e) {
            throw new VisExportException("Failed to build spatial tree.", e);
        }
    }
}
