/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences (HFT Stuttgart) <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.pipeline.stages;

import org.citydb.vis.attribute.AttributeEncoder;
import org.citydb.vis.options.Tiles3DFormatOptions;
import org.citydb.vis.pipeline.PipelineContext;
import org.citydb.vis.store.SpatialEntry;
import org.citydb.vis.store.VisExportStores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * The extent must be the exact union of all spatial-entry bounding boxes —
 * every later stage (grid partitioning, cell keying) derives cell geometry
 * from it, so an extent that misses an entry silently misplaces features.
 */
class ExtentComputationStageTest {

    @Test
    void extentIsUnionOfAllEntryBboxes(@TempDir Path tempDir) throws IOException {
        try (VisExportStores stores = new VisExportStores(2, tempDir.resolve("stores"))) {
            stores.getSpatialEntryStore().store(new SpatialEntry(1L, 8.15, 48.75,
                    new double[]{8.1, 48.7, 10.0, 8.2, 48.8, 25.0}, 0L, 0L), 0);
            stores.getSpatialEntryStore().store(new SpatialEntry(2L, 8.45, 48.65,
                    new double[]{8.4, 48.6, -5.0, 8.5, 48.7, 12.0}, 0L, 0L), 1);

            PipelineContext ctx = new PipelineContext(stores, new Tiles3DFormatOptions(),
                    new AttributeEncoder(), 2, 2);
            new ExtentComputationStage().execute(ctx);

            assertArrayEquals(new double[]{8.1, 48.6, -5.0, 8.5, 48.8, 25.0}, ctx.extent());
            assertNotNull(ctx.attrFields());
        }
    }

    @Test
    void singleEntryExtentEqualsItsBbox(@TempDir Path tempDir) throws IOException {
        try (VisExportStores stores = new VisExportStores(2, tempDir.resolve("stores"))) {
            double[] bbox = {8.1, 48.7, 0.0, 8.2, 48.8, 30.0};
            stores.getSpatialEntryStore().store(new SpatialEntry(1L, 8.15, 48.75, bbox, 0L, 0L), 0);

            PipelineContext ctx = new PipelineContext(stores, new Tiles3DFormatOptions(),
                    new AttributeEncoder(), 1, 2);
            new ExtentComputationStage().execute(ctx);

            assertArrayEquals(bbox, ctx.extent());
        }
    }
}
