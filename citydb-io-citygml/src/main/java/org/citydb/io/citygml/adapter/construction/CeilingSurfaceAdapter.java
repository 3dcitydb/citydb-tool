/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.io.citygml.adapter.construction;

import org.citydb.io.citygml.annotation.DatabaseType;
import org.citydb.io.citygml.builder.ModelBuildException;
import org.citydb.io.citygml.serializer.ModelSerializeException;
import org.citydb.model.common.Namespaces;
import org.citydb.model.feature.Feature;
import org.citydb.model.feature.FeatureType;
import org.citygml4j.core.model.construction.CeilingSurface;

@DatabaseType(name = "CeilingSurface", namespace = Namespaces.CONSTRUCTION)
public class CeilingSurfaceAdapter extends AbstractConstructionSurfaceAdapter<CeilingSurface> {

    @Override
    public Feature createModel(CeilingSurface source) throws ModelBuildException {
        return Feature.of(FeatureType.CEILING_SURFACE);
    }

    @Override
    public CeilingSurface createObject(Feature source) throws ModelSerializeException {
        return new CeilingSurface();
    }
}
