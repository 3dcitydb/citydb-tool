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

package org.citydb.io.citygml.adapter.construction;

import org.citydb.core.time.TimeHelper;
import org.citydb.io.citygml.adapter.gml.CodeAdapter;
import org.citydb.io.citygml.builder.ModelBuildException;
import org.citydb.io.citygml.builder.ModelBuilder;
import org.citydb.io.citygml.reader.ModelBuilderHelper;
import org.citydb.io.citygml.serializer.ModelSerializeException;
import org.citydb.io.citygml.serializer.ModelSerializer;
import org.citydb.io.citygml.writer.ModelSerializerHelper;
import org.citydb.model.common.Name;
import org.citydb.model.common.Namespaces;
import org.citydb.model.property.Attribute;
import org.citydb.model.property.DataType;
import org.citygml4j.core.model.construction.ConstructionEvent;

public class ConstructionEventAdapter implements ModelBuilder<ConstructionEvent, Attribute>, ModelSerializer<Attribute, ConstructionEvent> {

    @Override
    public void build(ConstructionEvent source, Attribute target, ModelBuilderHelper helper) throws ModelBuildException {
        helper.addAttribute(Name.of("event", Namespaces.CONSTRUCTION), source.getEvent(), target, CodeAdapter.class);

        if (source.getDateOfEvent() != null) {
            target.addProperty(Attribute.of(Name.of("dateOfEvent", Namespaces.CONSTRUCTION), DataType.TIMESTAMP)
                    .setTimeStamp(TimeHelper.toDateTime(source.getDateOfEvent())));
        }

        if (source.getDescription() != null) {
            target.addProperty(Attribute.of(Name.of("description", Namespaces.CONSTRUCTION), DataType.STRING)
                    .setStringValue(source.getDescription()));
        }

        target.setDataType(DataType.CONSTRUCTION_EVENT);
    }

    @Override
    public ConstructionEvent createObject(Attribute source) throws ModelSerializeException {
        return new ConstructionEvent();
    }

    @Override
    public void serialize(Attribute source, ConstructionEvent target, ModelSerializerHelper helper) throws ModelSerializeException {
        Attribute event = source.getProperties()
                .getFirst(Name.of("event", Namespaces.CONSTRUCTION), Attribute.class)
                .orElse(null);
        if (event != null) {
            target.setEvent(helper.getAttribute(event, CodeAdapter.class));
        }

        source.getProperties().getFirst(Name.of("dateOfEvent", Namespaces.CONSTRUCTION), Attribute.class)
                .flatMap(Attribute::getTimeStamp)
                .ifPresent(value -> target.setDateOfEvent(value.toLocalDate()));

        source.getProperties().getFirst(Name.of("description", Namespaces.CONSTRUCTION), Attribute.class)
                .flatMap(Attribute::getStringValue)
                .ifPresent(target::setDescription);
    }
}
