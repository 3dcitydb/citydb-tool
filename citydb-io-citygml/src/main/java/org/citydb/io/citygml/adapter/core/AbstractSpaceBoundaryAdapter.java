/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.io.citygml.adapter.core;

import org.citygml4j.core.model.core.AbstractSpaceBoundary;

public abstract class AbstractSpaceBoundaryAdapter<T extends AbstractSpaceBoundary> extends AbstractCityObjectAdapter<T> {
    final SpaceBoundaryGeometrySupport<T> geometrySupport = new SpaceBoundaryGeometrySupport<>();

    public AbstractSpaceBoundaryAdapter() {
        configureSerializer(geometrySupport);
    }

    protected void configureSerializer(SpaceBoundaryGeometrySupport<T> geometrySupport) {
    }
}
