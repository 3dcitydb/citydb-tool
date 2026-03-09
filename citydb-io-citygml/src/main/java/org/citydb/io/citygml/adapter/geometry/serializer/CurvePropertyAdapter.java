/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.io.citygml.adapter.geometry.serializer;

import org.citydb.io.citygml.serializer.ModelSerializeException;
import org.citydb.model.property.GeometryProperty;
import org.xmlobjects.gml.model.geometry.primitives.CurveProperty;

public class CurvePropertyAdapter extends AbstractGeometryPropertyAdapter<CurveProperty> {

    @Override
    public CurveProperty createObject(GeometryProperty source) throws ModelSerializeException {
        return new CurveProperty();
    }
}
