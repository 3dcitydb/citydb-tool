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
import org.citygml4j.core.model.construction.OuterCeilingSurface;

@DatabaseType(name = "OuterCeilingSurface", namespace = Namespaces.CONSTRUCTION)
public class OuterCeilingSurfaceAdapter extends AbstractConstructionSurfaceAdapter<OuterCeilingSurface> {

    @Override
    public Feature createModel(OuterCeilingSurface source) throws ModelBuildException {
        return Feature.of(FeatureType.OUTER_CEILING_SURFACE);
    }

    @Override
    public OuterCeilingSurface createObject(Feature source) throws ModelSerializeException {
        return new OuterCeilingSurface();
    }

    @Override
    protected void configureSerializer(SpaceBoundaryGeometrySupport<OuterCeilingSurface> geometrySupport) {
        geometrySupport.withLod2MultiSurface()
                .withLod3MultiSurface();
    }
}
