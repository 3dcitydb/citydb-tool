/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences (HFT Stuttgart) <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.pipeline.stages;

import org.citydb.vis.pipeline.PipelineContext;
import org.citydb.vis.pipeline.Stage;
import org.citydb.vis.store.SpatialEntry;
import org.citydb.vis.util.BoundingBoxUtils;

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
        double[] extent = BoundingBoxUtils.emptyAabb();
        Iterator<SpatialEntry> it = ctx.stores().getSpatialEntryStore().iterator();
        while (it.hasNext()) {
            BoundingBoxUtils.expandToBox(extent, it.next().bbox());
        }

        ctx.setExtent(extent);
        ctx.setAttrFields(ctx.attributeEncoder().finalizeFields(ctx.totalFeatures()));
    }
}
