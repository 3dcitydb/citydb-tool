/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences (HFT Stuttgart) <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.pipeline.stages;

import org.citydb.vis.VisExportException;
import org.citydb.vis.attribute.AttributeEncoder;
import org.citydb.vis.options.Tiles3DFormatOptions;
import org.citydb.vis.pipeline.PipelineContext;
import org.citydb.vis.store.PartitionedEntryStore;
import org.citydb.vis.store.SpatialEntry;
import org.citydb.vis.store.VisExportStores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Guards the grid sizing contract: {@code --grid-edge-length} is metric, the
 * extent is in EPSG:4326 degrees, and the conversion happens at the extent's
 * center latitude. Also verifies that entries actually land in the grid cell
 * matching their centroid, since every downstream LOD decision keys off that.
 */
class PartitioningStageTest {
    // ~0.02° of latitude is 2226.4 m at the WGS84 constant used by the stage;
    // chosen so expected grid dimensions are easy to derive by hand below.
    private static final double[] EXTENT = {0.0, 0.0, 0.0, 0.02, 0.02, 10.0};

    @Test
    void autoSentinelProducesSingleCell(@TempDir Path tempDir) throws IOException, VisExportException {
        try (VisExportStores stores = new VisExportStores(2, tempDir.resolve("stores"))) {
            stores.getSpatialEntryStore().store(entry(1L, 0.005, 0.005), 0);
            stores.getSpatialEntryStore().store(entry(2L, 0.015, 0.015), 0);

            // gridEdgeLength defaults to 0 — the "auto" sentinel.
            PipelineContext ctx = context(stores, new Tiles3DFormatOptions(), 2);
            new PartitioningStage().execute(ctx);

            assertNotNull(ctx.partitioned());
            assertEquals(1, ctx.partitioned().gridDim());
            assertEquals(1, ctx.partitioned().cellKeys().size());
            long onlyKey = ctx.partitioned().cellKeys().iterator().next();
            assertEquals(2, ctx.partitioned().loadCell(onlyKey).size());
            ctx.partitioned().close();
        }
    }

    @Test
    void gridDimIsCeilOfMetricRangeOverEdgeLength(@TempDir Path tempDir)
            throws IOException, VisExportException {
        try (VisExportStores stores = new VisExportStores(2, tempDir.resolve("stores"))) {
            stores.getSpatialEntryStore().store(entry(1L, 0.01, 0.01), 0);

            // Larger axis is 2226.4 m; ceil(2226.4 / 1000) = 3.
            Tiles3DFormatOptions opts = new Tiles3DFormatOptions();
            opts.setGridEdgeLength(1000.0);
            PipelineContext ctx = context(stores, opts, 1);
            new PartitioningStage().execute(ctx);

            assertEquals(3, ctx.partitioned().gridDim());
            ctx.partitioned().close();
        }
    }

    @Test
    void entriesLandInTheCellMatchingTheirCentroid(@TempDir Path tempDir)
            throws IOException, VisExportException {
        try (VisExportStores stores = new VisExportStores(2, tempDir.resolve("stores"))) {
            // One entry per quadrant of the extent; ceil(2226.4 / 1200) = 2.
            stores.getSpatialEntryStore().store(entry(1L, 0.005, 0.005), 0);
            stores.getSpatialEntryStore().store(entry(2L, 0.015, 0.005), 0);
            stores.getSpatialEntryStore().store(entry(3L, 0.005, 0.015), 1);
            stores.getSpatialEntryStore().store(entry(4L, 0.015, 0.015), 1);

            Tiles3DFormatOptions opts = new Tiles3DFormatOptions();
            opts.setGridEdgeLength(1200.0);
            PipelineContext ctx = context(stores, opts, 4);
            new PartitioningStage().execute(ctx);

            PartitionedEntryStore partitioned = ctx.partitioned();
            assertEquals(2, partitioned.gridDim());
            assertEquals(4, partitioned.cellKeys().size());

            // Map each feature id to its decoded [gy, gx] cell coordinates.
            Map<Long, int[]> cellOfFeature = new HashMap<>();
            for (long key : partitioned.cellKeys()) {
                List<SpatialEntry> cell = partitioned.loadCell(key);
                assertEquals(1, cell.size());
                cellOfFeature.put(cell.get(0).id(), PartitionedEntryStore.decodeKey(key, 2));
            }

            assertArrayEquals(new int[]{0, 0}, cellOfFeature.get(1L));
            assertArrayEquals(new int[]{0, 1}, cellOfFeature.get(2L));
            assertArrayEquals(new int[]{1, 0}, cellOfFeature.get(3L));
            assertArrayEquals(new int[]{1, 1}, cellOfFeature.get(4L));
            partitioned.close();
        }
    }

    private static SpatialEntry entry(long id, double cx, double cy) {
        return new SpatialEntry(id, cx, cy,
                new double[]{cx - 0.001, cy - 0.001, 0.0, cx + 0.001, cy + 0.001, 5.0}, 0L, 0L);
    }

    private static PipelineContext context(VisExportStores stores, Tiles3DFormatOptions opts,
                                           long totalFeatures) {
        return new PipelineContext(stores, opts, new AttributeEncoder(), totalFeatures, 2)
                .setExtent(EXTENT);
    }
}
