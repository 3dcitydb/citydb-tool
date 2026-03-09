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
import org.xmlobjects.gml.model.deprecated.StringOrRef;

public class StringOrRefAdapter implements ModelBuilder<StringOrRef, Attribute>, ModelSerializer<Attribute, StringOrRef> {

    @Override
    public void build(StringOrRef source, Attribute target, ModelBuilderHelper helper) throws ModelBuildException {
        target.setStringValue(source.getValue())
                .setURI(source.getHref())
                .setDataType(DataType.STRING_OR_REF);
    }

    @Override
    public StringOrRef createObject(Attribute source) throws ModelSerializeException {
        return new StringOrRef();
    }

    @Override
    public void serialize(Attribute source, StringOrRef target, ModelSerializerHelper helper) throws ModelSerializeException {
        source.getStringValue().ifPresent(target::setValue);
        source.getURI().ifPresent(target::setHref);
    }
}
