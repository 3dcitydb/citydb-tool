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
import org.xmlobjects.gml.model.basictypes.Measure;

public abstract class AbstractMeasureAdapter<T extends Measure> implements ModelBuilder<T, Attribute>, ModelSerializer<Attribute, T> {

    @Override
    public void build(T source, Attribute target, ModelBuilderHelper helper) throws ModelBuildException {
        target.setDoubleValue(source.getValue())
                .setUom(source.getUom())
                .setDataType(DataType.MEASURE);
    }

    @Override
    public void serialize(Attribute source, T target, ModelSerializerHelper helper) throws ModelSerializeException {
        source.getDoubleValue().ifPresent(target::setValue);
        source.getUom().ifPresent(target::setUom);
    }
}
