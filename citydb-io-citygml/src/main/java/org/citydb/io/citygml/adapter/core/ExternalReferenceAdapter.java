/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.io.citygml.adapter.core;

import org.citydb.io.citygml.builder.ModelBuildException;
import org.citydb.io.citygml.builder.ModelBuilder;
import org.citydb.io.citygml.reader.ModelBuilderHelper;
import org.citydb.io.citygml.serializer.ModelSerializeException;
import org.citydb.io.citygml.serializer.ModelSerializer;
import org.citydb.io.citygml.writer.ModelSerializerHelper;
import org.citydb.model.property.Attribute;
import org.citydb.model.property.DataType;
import org.citygml4j.core.model.core.ExternalReference;

public class ExternalReferenceAdapter implements ModelBuilder<ExternalReference, Attribute>, ModelSerializer<Attribute, ExternalReference> {

    @Override
    public void build(ExternalReference source, Attribute target, ModelBuilderHelper helper) throws ModelBuildException {
        target.setURI(source.getTargetResource())
                .setStringValue(source.getRelationType())
                .setCodeSpace(source.getInformationSystem())
                .setDataType(DataType.EXTERNAL_REFERENCE);
    }

    @Override
    public ExternalReference createObject(Attribute source) throws ModelSerializeException {
        return new ExternalReference();
    }

    @Override
    public void serialize(Attribute source, ExternalReference target, ModelSerializerHelper helper) throws ModelSerializeException {
        source.getURI().ifPresent(target::setTargetResource);
        source.getStringValue().ifPresent(target::setRelationType);
        source.getCodeSpace().ifPresent(target::setInformationSystem);
    }
}
