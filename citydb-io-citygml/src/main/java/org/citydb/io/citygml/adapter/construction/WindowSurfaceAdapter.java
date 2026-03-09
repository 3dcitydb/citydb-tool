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
import org.citygml4j.core.model.construction.WindowSurface;

@DatabaseType(name = "WindowSurface", namespace = Namespaces.CONSTRUCTION)
public class WindowSurfaceAdapter extends AbstractFillingSurfaceAdapter<WindowSurface> {

    @Override
    public Feature createModel(WindowSurface source) throws ModelBuildException {
        return Feature.of(FeatureType.WINDOW_SURFACE);
    }

    @Override
    public WindowSurface createObject(Feature source) throws ModelSerializeException {
        return new WindowSurface();
    }
}
