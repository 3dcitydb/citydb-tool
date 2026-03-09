/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.io.citygml.adapter.construction;

import org.citydb.io.citygml.adapter.core.SpaceBoundaryGeometrySupport;
import org.citydb.io.citygml.annotation.DatabaseType;
import org.citydb.io.citygml.builder.ModelBuildException;
import org.citydb.io.citygml.serializer.ModelSerializeException;
import org.citydb.model.common.Namespaces;
import org.citydb.model.feature.Feature;
import org.citydb.model.feature.FeatureType;
import org.citygml4j.core.model.construction.OuterFloorSurface;

@DatabaseType(name = "OuterFloorSurface", namespace = Namespaces.CONSTRUCTION)
public class OuterFloorSurfaceAdapter extends AbstractConstructionSurfaceAdapter<OuterFloorSurface> {

    @Override
    public Feature createModel(OuterFloorSurface source) throws ModelBuildException {
        return Feature.of(FeatureType.OUTER_FLOOR_SURFACE);
    }

    @Override
    public OuterFloorSurface createObject(Feature source) throws ModelSerializeException {
        return new OuterFloorSurface();
    }

    @Override
    protected void configureSerializer(SpaceBoundaryGeometrySupport<OuterFloorSurface> geometrySupport) {
        geometrySupport.withLod2MultiSurface()
                .withLod3MultiSurface();
    }
}
