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
import org.citygml4j.core.model.generics.DoubleAttribute;

@DatabaseType(name = "DoubleAttribute", namespace = Namespaces.GENERICS)
public class DoubleAttributeAdapter extends AbstractGenericAttributeAdapter<DoubleAttribute> {

    @Override
    public void build(DoubleAttribute source, Attribute target, ModelBuilderHelper helper) throws ModelBuildException {
        target.setDoubleValue(source.getValue())
                .setDataType(DataType.DOUBLE);
    }

    @Override
    public DoubleAttribute createObject(Attribute source) throws ModelSerializeException {
        return new DoubleAttribute();
    }

    @Override
    public void serialize(Attribute source, DoubleAttribute target, ModelSerializerHelper helper) throws ModelSerializeException {
        super.serialize(source, target, helper);
        source.getDoubleValue().ifPresent(target::setValue);
    }
}
