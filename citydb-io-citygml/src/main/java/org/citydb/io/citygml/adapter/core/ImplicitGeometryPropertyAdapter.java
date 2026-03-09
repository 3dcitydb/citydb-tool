/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.io.citygml.adapter.core;

import org.citydb.io.citygml.serializer.ModelSerializeException;
import org.citydb.io.citygml.serializer.ModelSerializer;
import org.citydb.io.citygml.writer.ModelSerializerHelper;
import org.citydb.model.property.ImplicitGeometryProperty;

public class ImplicitGeometryPropertyAdapter implements ModelSerializer<ImplicitGeometryProperty, org.citygml4j.core.model.core.ImplicitGeometryProperty> {

    @Override
    public org.citygml4j.core.model.core.ImplicitGeometryProperty createObject(ImplicitGeometryProperty source) throws ModelSerializeException {
        return new org.citygml4j.core.model.core.ImplicitGeometryProperty();
    }

    @Override
    public void serialize(ImplicitGeometryProperty source, org.citygml4j.core.model.core.ImplicitGeometryProperty target, ModelSerializerHelper helper) throws ModelSerializeException {
        target.setInlineObjectIfValid(helper.getImplicitGeometry(source));
    }
}
