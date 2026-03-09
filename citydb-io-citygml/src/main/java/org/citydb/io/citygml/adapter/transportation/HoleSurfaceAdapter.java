/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.io.citygml.adapter.transportation;

import org.citydb.io.citygml.adapter.core.AbstractThematicSurfaceAdapter;
import org.citydb.io.citygml.annotation.DatabaseType;
import org.citydb.io.citygml.builder.ModelBuildException;
import org.citydb.io.citygml.serializer.ModelSerializeException;
import org.citydb.model.common.Namespaces;
import org.citydb.model.feature.Feature;
import org.citydb.model.feature.FeatureType;
import org.citygml4j.core.model.transportation.HoleSurface;

@DatabaseType(name = "HoleSurface", namespace = Namespaces.TRANSPORTATION)
public class HoleSurfaceAdapter extends AbstractThematicSurfaceAdapter<HoleSurface> {

    @Override
    public Feature createModel(HoleSurface source) throws ModelBuildException {
        return Feature.of(FeatureType.HOLE_SURFACE);
    }

    @Override
    public HoleSurface createObject(Feature source) throws ModelSerializeException {
        return new HoleSurface();
    }
}
