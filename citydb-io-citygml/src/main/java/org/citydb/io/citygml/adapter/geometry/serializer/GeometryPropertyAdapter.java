/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.io.citygml.adapter.geometry.serializer;

import org.citydb.io.citygml.serializer.ModelSerializeException;
import org.xmlobjects.gml.model.geometry.GeometryProperty;

public class GeometryPropertyAdapter extends AbstractGeometryPropertyAdapter<GeometryProperty<?>> {

    @Override
    public GeometryProperty<?> createObject(org.citydb.model.property.GeometryProperty source) throws ModelSerializeException {
        return new GeometryProperty<>();
    }
}
