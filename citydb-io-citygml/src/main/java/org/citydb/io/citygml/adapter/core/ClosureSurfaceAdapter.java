/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.io.citygml.adapter.core;

import org.citydb.io.citygml.annotation.DatabaseType;
import org.citydb.io.citygml.builder.ModelBuildException;
import org.citydb.io.citygml.serializer.ModelSerializeException;
import org.citydb.model.common.Namespaces;
import org.citydb.model.feature.Feature;
import org.citydb.model.feature.FeatureType;
import org.citygml4j.core.model.core.ClosureSurface;

@DatabaseType(name = "ClosureSurface", namespace = Namespaces.CORE)
public class ClosureSurfaceAdapter extends AbstractThematicSurfaceAdapter<ClosureSurface> {

    @Override
    public Feature createModel(ClosureSurface source) throws ModelBuildException {
        return Feature.of(FeatureType.CLOSURE_SURFACE);
    }

    @Override
    public ClosureSurface createObject(Feature source) throws ModelSerializeException {
        return new ClosureSurface();
    }

    @Override
    protected void configureSerializer(SpaceBoundaryGeometrySupport<ClosureSurface> geometrySupport) {
        geometrySupport.withLod2MultiSurface()
                .withLod3MultiSurface();
    }
}
