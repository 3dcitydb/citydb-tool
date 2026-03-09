/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.io.citygml.adapter.geometry.serializer;

import org.citydb.io.citygml.serializer.ModelSerializeException;
import org.citydb.model.property.GeometryProperty;
import org.citygml4j.core.model.relief.TinProperty;

public class TinPropertyAdapter extends AbstractGeometryPropertyAdapter<TinProperty> {

    @Override
    public TinProperty createObject(GeometryProperty source) throws ModelSerializeException {
        return new TinProperty();
    }
}
