/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.io.citygml.adapter.gml;

import org.citydb.io.citygml.builder.ModelBuildException;
import org.citydb.io.citygml.builder.ModelBuilder;
import org.citydb.io.citygml.reader.ModelBuilderHelper;
import org.citydb.io.citygml.serializer.ModelSerializeException;
import org.citydb.io.citygml.serializer.ModelSerializer;
import org.citydb.io.citygml.writer.ModelSerializerHelper;
import org.citydb.model.property.Attribute;
import org.citydb.model.property.DataType;
import org.xmlobjects.gml.model.basictypes.Code;

public class CodeAdapter implements ModelBuilder<Code, Attribute>, ModelSerializer<Attribute, Code> {

    @Override
    public void build(Code source, Attribute target, ModelBuilderHelper helper) throws ModelBuildException {
        target.setStringValue(source.getValue())
                .setCodeSpace(source.getCodeSpace())
                .setDataType(DataType.CODE);
    }

    @Override
    public Code createObject(Attribute source) throws ModelSerializeException {
        return new Code();
    }

    @Override
    public void serialize(Attribute source, Code target, ModelSerializerHelper helper) throws ModelSerializeException {
        source.getStringValue().ifPresent(target::setValue);
        source.getCodeSpace().ifPresent(target::setCodeSpace);
    }
}
