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
import org.citygml4j.core.model.construction.RoofSurface;

@DatabaseType(name = "RoofSurface", namespace = Namespaces.CONSTRUCTION)
public class RoofSurfaceAdapter extends AbstractConstructionSurfaceAdapter<RoofSurface> {

    @Override
    public Feature createModel(RoofSurface source) throws ModelBuildException {
        return Feature.of(FeatureType.ROOF_SURFACE);
    }

    @Override
    public RoofSurface createObject(Feature source) throws ModelSerializeException {
        return new RoofSurface();
    }

    @Override
    protected void configureSerializer(SpaceBoundaryGeometrySupport<RoofSurface> geometrySupport) {
        geometrySupport.withLod2MultiSurface()
                .withLod3MultiSurface();
    }
}
