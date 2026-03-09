/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.io.citygml.adapter.generics;

import org.citydb.io.citygml.adapter.core.AbstractGenericAttributeAdapter;
import org.citydb.io.citygml.adapter.gml.CodeAdapter;
import org.citydb.io.citygml.annotation.DatabaseType;
import org.citydb.io.citygml.builder.ModelBuildException;
import org.citydb.io.citygml.reader.ModelBuilderHelper;
import org.citydb.io.citygml.serializer.ModelSerializeException;
import org.citydb.io.citygml.writer.ModelSerializerHelper;
import org.citydb.model.common.Namespaces;
import org.citydb.model.property.Attribute;
import org.citydb.model.property.DataType;
import org.citygml4j.core.model.generics.CodeAttribute;

@DatabaseType(name = "CodeAttribute", namespace = Namespaces.GENERICS)
public class CodeAttributeAdapter extends AbstractGenericAttributeAdapter<CodeAttribute> {

    @Override
    public void build(CodeAttribute source, Attribute target, ModelBuilderHelper helper) throws ModelBuildException {
        if (source.getValue() != null) {
            target.setStringValue(source.getValue().getValue())
                    .setCodeSpace(source.getValue().getCodeSpace());
        }

        target.setDataType(DataType.CODE);
    }

    @Override
    public CodeAttribute createObject(Attribute source) throws ModelSerializeException {
        return new CodeAttribute();
    }

    @Override
    public void serialize(Attribute source, CodeAttribute target, ModelSerializerHelper helper) throws ModelSerializeException {
        super.serialize(source, target, helper);
        target.setValue(helper.getAttribute(source, CodeAdapter.class));
    }
}
