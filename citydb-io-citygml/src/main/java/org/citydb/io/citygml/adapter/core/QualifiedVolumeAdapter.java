/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.io.citygml.adapter.core;

import org.citydb.io.citygml.adapter.gml.CodeAdapter;
import org.citydb.io.citygml.adapter.gml.VolumeAdapter;
import org.citydb.io.citygml.builder.ModelBuildException;
import org.citydb.io.citygml.builder.ModelBuilder;
import org.citydb.io.citygml.reader.ModelBuilderHelper;
import org.citydb.io.citygml.serializer.ModelSerializeException;
import org.citydb.io.citygml.serializer.ModelSerializer;
import org.citydb.io.citygml.writer.ModelSerializerHelper;
import org.citydb.model.property.Attribute;
import org.citydb.model.property.DataType;
import org.citygml4j.core.model.core.QualifiedVolume;

public class QualifiedVolumeAdapter implements ModelBuilder<QualifiedVolume, Attribute>, ModelSerializer<Attribute, QualifiedVolume> {

    @Override
    public void build(QualifiedVolume source, Attribute target, ModelBuilderHelper helper) throws ModelBuildException {
        if (source.getVolume() != null) {
            helper.getOrCreateBuilder(VolumeAdapter.class).build(source.getVolume(), target, helper);
        }

        if (source.getTypeOfVolume() != null) {
            helper.getOrCreateBuilder(CodeAdapter.class).build(source.getTypeOfVolume(), target, helper);
        }

        target.setDataType(DataType.QUALIFIED_VOLUME);
    }

    @Override
    public QualifiedVolume createObject(Attribute source) throws ModelSerializeException {
        return new QualifiedVolume();
    }

    @Override
    public void serialize(Attribute source, QualifiedVolume target, ModelSerializerHelper helper) throws ModelSerializeException {
        target.setVolume(helper.getAttribute(source, VolumeAdapter.class));
        target.setTypeOfVolume(helper.getAttribute(source, CodeAdapter.class));
    }
}
