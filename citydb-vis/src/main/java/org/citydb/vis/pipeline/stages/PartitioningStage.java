/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.vis.pipeline.stages;

import org.citydb.vis.writer.VisExportException;
import org.citydb.vis.pipeline.PipelineContext;
import org.citydb.vis.pipeline.Stage;
import org.citydb.vis.store.PartitionedEntryStore;

import java.io.IOException;

/**
 * Two-pass histogram + scatter partitioning of spatial entries into a grid.
 * The grid dimension is sized so each cell holds roughly
 * {@code 50 × maxFeaturesPerNode} entries (minimum 3000) — enough to amortize
 * quadtree construction without blowing per-cell heap.
 * <p>
 * Populates {@link PipelineContext#partitioned()}. The store is closed by
 * {@link TreeBuildingStage} after its entries have been consumed.
 */
public final class PartitioningStage implements Stage {
    @Override
    public void execute(PipelineContext ctx) throws VisExportException {
        int targetPerCell = Math.max(ctx.formatOptions().getMaxFeaturesPerNode() * 50, 3000);
        int targetCells = Math.max(1, (int) (ctx.totalFeatures() / targetPerCell));
        int gridDim = Math.max(1, (int) Math.ceil(Math.sqrt(targetCells)));

        try {
            ctx.setPartitioned(PartitionedEntryStore.create(
                    ctx.stores().getSpatialEntryStore(), ctx.extent(), gridDim,
                    ctx.stores().getTempDir()));
        } catch (IOException e) {
            throw new VisExportException("Failed to partition spatial entries.", e);
        }
    }
}
