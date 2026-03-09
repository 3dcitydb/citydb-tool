/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.io.citygml.adapter.generics;

import org.citydb.io.citygml.adapter.core.AbstractGenericAttributeAdapter;
import org.citydb.io.citygml.annotation.DatabaseType;
import org.citydb.io.citygml.builder.ModelBuildException;
import org.citydb.io.citygml.reader.ModelBuilderHelper;
import org.citydb.io.citygml.serializer.ModelSerializeException;
import org.citydb.io.citygml.writer.ModelSerializerHelper;
import org.citydb.model.common.Namespaces;
import org.citydb.model.property.Attribute;
import org.citydb.model.property.DataType;
import org.citygml4j.core.model.generics.StringAttribute;

@DatabaseType(name = "StringAttribute", namespace = Namespaces.GENERICS)
public class StringAttributeAdapter extends AbstractGenericAttributeAdapter<StringAttribute> {

    @Override
    public void build(StringAttribute source, Attribute target, ModelBuilderHelper helper) throws ModelBuildException {
        target.setStringValue(source.getValue())
                .setDataType(DataType.STRING);
    }

    @Override
    public StringAttribute createObject(Attribute source) throws ModelSerializeException {
        return new StringAttribute();
    }

    @Override
    public void serialize(Attribute source, StringAttribute target, ModelSerializerHelper helper) throws ModelSerializeException {
        super.serialize(source, target, helper);
        source.getStringValue().ifPresent(target::setValue);
    }
}
