/*
 * citydb-tool - Command-line tool for the 3D City Database
 * https://www.3dcitydb.org/
 *
 * Copyright 2022-2023
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

package org.citydb.io.citygml.adapter.building;

import org.citydb.io.citygml.adapter.gml.CodeAdapter;
import org.citydb.io.citygml.adapter.gml.LengthAdapter;
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
import org.citygml4j.core.model.building.RoomHeight;
import org.citygml4j.core.model.construction.HeightStatusValue;

public class RoomHeightAdapter implements ModelBuilder<RoomHeight, Attribute>, ModelSerializer<Attribute, RoomHeight> {

    @Override
    public void build(RoomHeight source, Attribute target, ModelBuilderHelper helper) throws ModelBuildException {
        helper.addAttribute(Name.of("value", Namespaces.BUILDING), source.getValue(), target, LengthAdapter.class);

        if (source.getStatus() != null) {
            target.addProperty(Attribute.of(Name.of("status", Namespaces.BUILDING), DataType.STRING)
                    .setStringValue(source.getStatus().toValue()));
        }

        helper.addAttribute(Name.of("lowReference", Namespaces.BUILDING), source.getLowReference(), target,
                CodeAdapter.class);
        helper.addAttribute(Name.of("highReference", Namespaces.BUILDING), source.getHighReference(), target,
                CodeAdapter.class);

        target.setDataType(DataType.ROOM_HEIGHT);
    }

    @Override
    public RoomHeight createObject(Attribute source) throws ModelSerializeException {
        return new RoomHeight();
    }

    @Override
    public void serialize(Attribute source, RoomHeight target, ModelSerializerHelper helper) throws ModelSerializeException {
        Attribute value = source.getProperties()
                .getFirst(Name.of("value", Namespaces.CONSTRUCTION), Attribute.class)
                .orElse(null);
        if (value != null) {
            target.setValue(helper.getAttribute(value, LengthAdapter.class));
        }

        source.getProperties().getFirst(Name.of("status", Namespaces.CONSTRUCTION), Attribute.class)
                .flatMap(Attribute::getStringValue)
                .ifPresent(status -> target.setStatus(HeightStatusValue.fromValue(status)));

        Attribute lowReference = source.getProperties()
                .getFirst(Name.of("lowReference", Namespaces.CONSTRUCTION), Attribute.class)
                .orElse(null);
        if (lowReference != null) {
            target.setLowReference(helper.getAttribute(lowReference, CodeAdapter.class));
        }

        Attribute highReference = source.getProperties()
                .getFirst(Name.of("highReference", Namespaces.CONSTRUCTION), Attribute.class)
                .orElse(null);
        if (highReference != null) {
            target.setHighReference(helper.getAttribute(highReference, CodeAdapter.class));
        }
    }
}
