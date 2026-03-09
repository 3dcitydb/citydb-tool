/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.io.citygml.adapter.waterbody;

import org.citydb.io.citygml.adapter.core.SpaceBoundaryGeometrySupport;
import org.citydb.io.citygml.annotation.DatabaseType;
import org.citydb.io.citygml.builder.ModelBuildException;
import org.citydb.io.citygml.serializer.ModelSerializeException;
import org.citydb.model.common.Namespaces;
import org.citydb.model.feature.Feature;
import org.citydb.model.feature.FeatureType;
import org.citygml4j.core.model.waterbody.WaterGroundSurface;

@DatabaseType(name = "WaterGroundSurface", namespace = Namespaces.WATER_BODY)
public class WaterGroundSurfaceAdapter extends AbstractWaterBoundarySurfaceAdapter<WaterGroundSurface> {

    @Override
    public Feature createModel(WaterGroundSurface source) throws ModelBuildException {
        return Feature.of(FeatureType.WATER_GROUND_SURFACE);
    }

    @Override
    public WaterGroundSurface createObject(Feature source) throws ModelSerializeException {
        return new WaterGroundSurface();
    }

    @Override
    protected void configureSerializer(SpaceBoundaryGeometrySupport<WaterGroundSurface> geometrySupport) {
        geometrySupport.withLod2MultiSurface()
                .withLod3MultiSurface();
    }
}
