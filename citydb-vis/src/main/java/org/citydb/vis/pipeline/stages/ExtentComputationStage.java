/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences (HFT Stuttgart) <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.pipeline.stages;

import org.citydb.vis.pipeline.PipelineContext;
import org.citydb.vis.pipeline.Stage;
import org.citydb.vis.store.SpatialEntry;

import java.util.Iterator;

/**
 * Streams all {@link SpatialEntry} bounding boxes from the spatial store to
 * compute the dataset extent, and finalizes the incrementally tracked
 * attribute fields. Populates {@link PipelineContext#extent()} and
 * {@link PipelineContext#attrFields()}.
 */
public final class ExtentComputationStage implements Stage {
    @Override
    public void execute(PipelineContext ctx) {
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, minZ = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;

        Iterator<SpatialEntry> it = ctx.stores().getSpatialEntryStore().iterator();
        while (it.hasNext()) {
            SpatialEntry e = it.next();
            double[] bb = e.bbox();
            if (bb[0] < minX) minX = bb[0];
            if (bb[1] < minY) minY = bb[1];
            if (bb[2] < minZ) minZ = bb[2];
            if (bb[3] > maxX) maxX = bb[3];
            if (bb[4] > maxY) maxY = bb[4];
            if (bb[5] > maxZ) maxZ = bb[5];
        }

        ctx.setExtent(new double[]{minX, minY, minZ, maxX, maxY, maxZ});
        ctx.setAttrFields(ctx.attributeEncoder().finalizeFields(ctx.totalFeatures()));
    }
}
