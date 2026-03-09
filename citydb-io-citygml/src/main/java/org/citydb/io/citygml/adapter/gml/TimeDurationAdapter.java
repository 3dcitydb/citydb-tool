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
import org.xmlobjects.gml.model.temporal.TimeDuration;

public class TimeDurationAdapter implements ModelBuilder<TimeDuration, Attribute>, ModelSerializer<Attribute, TimeDuration> {

    @Override
    public void build(TimeDuration source, Attribute target, ModelBuilderHelper helper) throws ModelBuildException {
        target.setStringValue(source.getValue() != null ?
                        source.getValue().toString() :
                        null)
                .setDataType(DataType.DURATION);
    }

    @Override
    public TimeDuration createObject(Attribute source) throws ModelSerializeException {
        return new TimeDuration();
    }

    @Override
    public void serialize(Attribute source, TimeDuration target, ModelSerializerHelper helper) throws ModelSerializeException {
        source.getStringValue().ifPresent(target::setValue);
    }
}
