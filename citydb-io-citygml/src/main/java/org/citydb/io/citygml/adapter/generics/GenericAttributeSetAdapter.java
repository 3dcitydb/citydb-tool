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
import org.citydb.model.property.Property;
import org.citygml4j.core.model.core.AbstractGenericAttribute;
import org.citygml4j.core.model.core.AbstractGenericAttributeProperty;
import org.citygml4j.core.model.generics.GenericAttributeSet;

@DatabaseType(name = "GenericAttributeSet", namespace = Namespaces.GENERICS)
public class GenericAttributeSetAdapter extends AbstractGenericAttributeAdapter<GenericAttributeSet> {

    @Override
    public void build(GenericAttributeSet source, Attribute target, ModelBuilderHelper helper) throws ModelBuildException {
        if (source.isSetValue()) {
            for (AbstractGenericAttributeProperty property : source.getValue()) {
                if (property.getObject() != null) {
                    helper.addAttribute(property.getObject(), target);
                }
            }
        }

        target.setDataType(DataType.GENERIC_ATTRIBUTE_SET);
    }

    @Override
    public GenericAttributeSet createObject(Attribute source) throws ModelSerializeException {
        return new GenericAttributeSet();
    }

    @Override
    public void serialize(Attribute source, GenericAttributeSet target, ModelSerializerHelper helper) throws ModelSerializeException {
        super.serialize(source, target, helper);

        for (Property<?> property : source.getProperties().getByNamespace(Namespaces.GENERICS)) {
            if (property instanceof Attribute attribute) {
                AbstractGenericAttribute<?> genericAttribute = helper.getGenericAttribute(attribute);
                if (genericAttribute != null) {
                    target.getValue().add(new AbstractGenericAttributeProperty(genericAttribute));
                }
            }
        }
    }
}
