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

package org.citydb.io.citygml.adapter.core;

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
import org.citygml4j.core.model.core.Occupancy;

public class OccupancyAdapter implements ModelBuilder<Occupancy, Attribute>, ModelSerializer<Attribute, Occupancy> {

    @Override
    public void build(Occupancy source, Attribute target, ModelBuilderHelper helper) throws ModelBuildException {
        if (source.getNumberOfOccupants() != null) {
            target.addProperty(Attribute.of(Name.of("numberOfOccupants", Namespaces.CORE), DataType.INTEGER)
                    .setIntValue(source.getNumberOfOccupants()));
        }

        helper.addAttribute(Name.of("interval", Namespaces.CORE), source.getInterval(), target, CodeAdapter.class);
        helper.addAttribute(Name.of("occupantType", Namespaces.CORE), source.getOccupantType(), target,
                CodeAdapter.class);

        target.setDataType(DataType.OCCUPANCY);
    }

    @Override
    public Occupancy createObject(Attribute source) throws ModelSerializeException {
        return new Occupancy();
    }

    @Override
    public void serialize(Attribute source, Occupancy target, ModelSerializerHelper helper) throws ModelSerializeException {
        source.getProperties().getFirst(Name.of("numberOfOccupants", Namespaces.CORE), Attribute.class)
                .flatMap(Attribute::getIntValue)
                .ifPresent(value -> target.setNumberOfOccupants(value.intValue()));

        Attribute interval = source.getProperties()
                .getFirst(Name.of("interval", Namespaces.CORE), Attribute.class)
                .orElse(null);
        if (interval != null) {
            target.setInterval(helper.getAttribute(interval, CodeAdapter.class));
        }

        Attribute occupantType = source.getProperties()
                .getFirst(Name.of("occupantType", Namespaces.CORE), Attribute.class)
                .orElse(null);
        if (occupantType != null) {
            target.setOccupantType(helper.getAttribute(occupantType, CodeAdapter.class));
        }
    }
}
