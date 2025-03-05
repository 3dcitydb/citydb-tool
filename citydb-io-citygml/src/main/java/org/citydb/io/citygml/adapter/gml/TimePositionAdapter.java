/*
 * citydb-tool - Command-line tool for the 3D City Database
 * https://www.3dcitydb.org/
 *
 * Copyright 2022-2024
 * virtualcitysystems GmbH, Germany
 * https://vc.systems/
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.citydb.io.citygml.adapter.gml;

import org.apache.logging.log4j.Level;
import org.citydb.core.time.TimeHelper;
import org.citydb.io.citygml.builder.ModelBuildException;
import org.citydb.io.citygml.builder.ModelBuilder;
import org.citydb.io.citygml.reader.ModelBuilderHelper;
import org.citydb.io.citygml.serializer.ModelSerializeException;
import org.citydb.io.citygml.serializer.ModelSerializer;
import org.citydb.io.citygml.writer.ModelSerializerHelper;
import org.citydb.model.property.Attribute;
import org.citydb.model.property.DataType;
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
