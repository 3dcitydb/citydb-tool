/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences (HFT Stuttgart) <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.pipeline.stages;

import org.citydb.vis.VisExportException;
import org.citydb.vis.pipeline.PipelineContext;
import org.citydb.vis.pipeline.Stage;
import org.citydb.vis.store.PartitionedEntryStore;
import org.citydb.vis.util.GeoTransform;

import java.io.IOException;

/**
 * Two-pass histogram + scatter partitioning of spatial entries into a grid.
 * <p>
 * The grid dimension is derived from the dataset extent and the configured
 * {@code --grid-edge-length} (in meters). Each grid cell therefore covers a
 * fixed physical footprint regardless of feature density, giving predictable
 * LOD load distances once the spatial aggregation tree ({@link
 * org.citydb.vis.scene.CellAggregator}) is built on top.
 * <p>
 * The source extent is in EPSG:4326 degrees; it is converted to meters using
 * {@link GeoTransform#metersPerDegreeLon(double)} evaluated at the extent's
 * center latitude. The grid uses the same count along both axes, sized
 * from the larger X/Y range: cells are near-square when the data extent is
 * near-square (typical for city footprints) and become rectangular along
 * the minor axis for elongated extents.
 * <p>
 * Populates {@link PipelineContext#partitioned()}. The store is closed by
 * {@link TreeBuildingStage} after its entries have been consumed.
 */
public final class PartitioningStage implements Stage {
    @Override
    public void execute(PipelineContext ctx) throws VisExportException {
        int gridDim = computeGridDim(ctx.extent(), ctx.formatOptions().getGridEdgeLength());

        try {
            ctx.setPartitioned(PartitionedEntryStore.create(
                    ctx.stores().getSpatialEntryStore(), ctx.extent(), gridDim,
                    ctx.stores().getTempDir()));
        } catch (IOException e) {
            throw new VisExportException("Failed to partition spatial entries.", e);
        }
    }

    private static int computeGridDim(double[] extent, double edgeLengthMeters) {
        // edgeLengthMeters <= 0 is the "auto" sentinel: size one cell to the
        // dataset's longest side so the whole dataset lands in a single root
        // cell. Equivalent to gridDim = 1 — no spatial subdivision.
        if (edgeLengthMeters <= 0) {
            return 1;
        }
        double centerLat = (extent[1] + extent[4]) / 2;
        double rangeXMeters = (extent[3] - extent[0]) * GeoTransform.metersPerDegreeLon(centerLat);
        double rangeYMeters = (extent[4] - extent[1]) * GeoTransform.WGS84_METERS_PER_DEGREE_LAT;
        double maxRange = Math.max(rangeXMeters, rangeYMeters);
        return Math.max(1, (int) Math.ceil(maxRange / edgeLengthMeters));
    }
}
