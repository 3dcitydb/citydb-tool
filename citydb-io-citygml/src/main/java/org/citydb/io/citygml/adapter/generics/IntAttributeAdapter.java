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
import org.citygml4j.core.model.generics.IntAttribute;

@DatabaseType(name = "IntAttribute", namespace = Namespaces.GENERICS)
public class IntAttributeAdapter extends AbstractGenericAttributeAdapter<IntAttribute> {

    @Override
    public void build(IntAttribute source, Attribute target, ModelBuilderHelper helper) throws ModelBuildException {
        target.setIntValue(source.getValue())
                .setDataType(DataType.INTEGER);
    }

    @Override
    public IntAttribute createObject(Attribute source) throws ModelSerializeException {
        return new IntAttribute();
    }

    @Override
    public void serialize(Attribute source, IntAttribute target, ModelSerializerHelper helper) throws ModelSerializeException {
        super.serialize(source, target, helper);
        source.getIntValue().ifPresent(value -> target.setValue(value.intValue()));
    }
}
