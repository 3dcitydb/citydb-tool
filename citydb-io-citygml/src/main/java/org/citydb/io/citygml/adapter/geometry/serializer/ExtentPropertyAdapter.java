/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.io.citygml.adapter.geometry.serializer;

import org.citydb.io.citygml.serializer.ModelSerializeException;
import org.citydb.model.property.GeometryProperty;
import org.citygml4j.core.model.relief.ExtentProperty;

public class ExtentPropertyAdapter extends AbstractGeometryPropertyAdapter<ExtentProperty> {

    @Override
    public ExtentProperty createObject(GeometryProperty source) throws ModelSerializeException {
        return new ExtentProperty();
    }
}
