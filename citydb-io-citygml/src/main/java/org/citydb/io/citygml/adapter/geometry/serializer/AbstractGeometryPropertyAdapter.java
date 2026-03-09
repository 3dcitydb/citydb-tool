/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.io.citygml.adapter.geometry.serializer;

import org.citydb.io.citygml.serializer.ModelSerializeException;
import org.citydb.io.citygml.serializer.ModelSerializer;
import org.citydb.io.citygml.writer.ModelSerializerHelper;
import org.xmlobjects.gml.model.geometry.GeometryProperty;

public abstract class AbstractGeometryPropertyAdapter<T extends GeometryProperty<?>> implements ModelSerializer<org.citydb.model.property.GeometryProperty, T> {

    @Override
    public void serialize(org.citydb.model.property.GeometryProperty source, T target, ModelSerializerHelper helper) throws ModelSerializeException {
        if (helper.lookupAndPut(source.getObject())) {
            target.setHref("#" + source.getObject().getOrCreateObjectId());
        } else {
            target.setInlineObjectIfValid(helper.getGeometry(source.getObject()));
        }
    }
}
