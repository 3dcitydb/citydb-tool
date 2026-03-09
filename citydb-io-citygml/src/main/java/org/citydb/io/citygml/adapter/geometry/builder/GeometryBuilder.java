/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.io.citygml.adapter.geometry.builder;

import org.citydb.io.citygml.builder.ModelBuildException;
import org.citydb.model.geometry.Geometry;
import org.xmlobjects.gml.model.geometry.AbstractGeometry;

public abstract class GeometryBuilder {
    abstract Geometry<?> build(AbstractGeometry source) throws ModelBuildException;
}
