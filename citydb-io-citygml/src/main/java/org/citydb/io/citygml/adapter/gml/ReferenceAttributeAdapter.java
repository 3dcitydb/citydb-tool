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
import org.xmlobjects.gml.model.base.Reference;

public class ReferenceAttributeAdapter implements ModelBuilder<Reference, Attribute>, ModelSerializer<Attribute, Reference> {

    @Override
    public void build(Reference source, Attribute target, ModelBuilderHelper helper) throws ModelBuildException {
        target.setURI(source.getHref())
                .setDataType(DataType.REFERENCE);
    }

    @Override
    public Reference createObject(Attribute source) throws ModelSerializeException {
        return new Reference();
    }

    @Override
    public void serialize(Attribute source, Reference target, ModelSerializerHelper helper) throws ModelSerializeException {
        source.getURI().ifPresent(target::setHref);
    }
}
