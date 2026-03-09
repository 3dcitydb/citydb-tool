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
import org.citygml4j.core.model.construction.GroundSurface;

@DatabaseType(name = "GroundSurface", namespace = Namespaces.CONSTRUCTION)
public class GroundSurfaceAdapter extends AbstractConstructionSurfaceAdapter<GroundSurface> {

    @Override
    public Feature createModel(GroundSurface source) throws ModelBuildException {
        return Feature.of(FeatureType.GROUND_SURFACE);
    }

    @Override
    public GroundSurface createObject(Feature source) throws ModelSerializeException {
        return new GroundSurface();
    }

    @Override
    protected void configureSerializer(SpaceBoundaryGeometrySupport<GroundSurface> geometrySupport) {
        geometrySupport.withLod2MultiSurface()
                .withLod3MultiSurface();
    }
}
