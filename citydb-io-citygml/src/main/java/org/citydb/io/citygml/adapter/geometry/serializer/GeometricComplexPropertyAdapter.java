/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.io.citygml.adapter.geometry.serializer;

import org.citydb.io.citygml.serializer.ModelSerializeException;
import org.citydb.model.property.GeometryProperty;
import org.xmlobjects.gml.model.geometry.complexes.GeometricComplexProperty;

public class GeometricComplexPropertyAdapter extends AbstractGeometryPropertyAdapter<GeometricComplexProperty> {

    @Override
    public GeometricComplexProperty createObject(GeometryProperty source) throws ModelSerializeException {
        return new GeometricComplexProperty();
    }
}
