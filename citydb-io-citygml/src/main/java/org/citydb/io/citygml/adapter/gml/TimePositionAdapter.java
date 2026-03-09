/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.io.citygml.adapter.gml;

import org.citydb.core.time.TimeHelper;
import org.citydb.io.citygml.builder.ModelBuildException;
import org.citydb.io.citygml.builder.ModelBuilder;
import org.citydb.io.citygml.reader.ModelBuilderHelper;
import org.citydb.io.citygml.serializer.ModelSerializeException;
import org.citydb.io.citygml.serializer.ModelSerializer;
import org.citydb.io.citygml.writer.ModelSerializerHelper;
import org.citydb.model.property.Attribute;
import org.citydb.model.property.DataType;
import org.slf4j.event.Level;
import org.xmlobjects.gml.model.base.AbstractGML;
import org.xmlobjects.gml.model.temporal.ClockTime;
import org.xmlobjects.gml.model.temporal.DateAndTime;
import org.xmlobjects.gml.model.temporal.TimePosition;

import java.time.OffsetDateTime;

public class TimePositionAdapter implements ModelBuilder<TimePosition, Attribute>, ModelSerializer<Attribute, TimePosition> {

    @Override
    public void build(TimePosition source, Attribute target, ModelBuilderHelper helper) throws ModelBuildException {
        OffsetDateTime value = null;
        if (source.isCalendarDate()) {
            value = source.asCalendarDate().getValue();
        } else if (source.isClockTime()) {
            value = TimeHelper.toDateTime(source.asClockTime().getValue());
        } else if (source.isDateAndTime()) {
            value = source.asDateAndTime().getValue();
        } else if (source.isTimeCoordinate()) {
            logOrThrowInvalidTimePosition(source, source.asTimeCoordinate().getValue(), helper);
        } else if (source.isOrdinalPosition()) {
            logOrThrowInvalidTimePosition(source, source.asOrdinalPosition().getValue(), helper);
        }

        target.setTimeStamp(value)
                .setDataType(DataType.TIME_POSITION);
    }

    @Override
    public TimePosition createObject(Attribute source) throws ModelSerializeException {
        return new TimePosition();
    }

    @Override
    public void serialize(Attribute source, TimePosition target, ModelSerializerHelper helper) throws ModelSerializeException {
        source.getTimeStamp().ifPresent(timePosition -> target.setValue(
                timePosition.toLocalDate().isEqual(TimeHelper.LOCAL_TIME_BASE_DATE.toLocalDate()) ?
                        new ClockTime(timePosition.toOffsetTime()) :
                        new DateAndTime(timePosition)));
    }

    private void logOrThrowInvalidTimePosition(TimePosition source, Object value, ModelBuilderHelper helper) throws ModelBuildException {
        helper.logOrThrow(Level.ERROR, helper.formatMessage(source.getParent(AbstractGML.class),
                "Failed to convert time position '" + value + "' to a timestamp."));
    }
}
