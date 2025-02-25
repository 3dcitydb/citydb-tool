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

package org.citydb.io.citygml.adapter.dynamizer;

import org.citydb.io.citygml.adapter.core.AbstractAppearancePropertyAdapter;
import org.citydb.io.citygml.adapter.core.ImplicitGeometryPropertyAdapter;
import org.citydb.io.citygml.adapter.geometry.builder.Lod;
import org.citydb.io.citygml.adapter.geometry.serializer.GeometryPropertyAdapter;
import org.citydb.io.citygml.adapter.gml.TimePositionAdapter;
import org.citydb.io.citygml.builder.ModelBuildException;
import org.citydb.io.citygml.builder.ModelBuilder;
import org.citydb.io.citygml.reader.ModelBuilderHelper;
import org.citydb.io.citygml.serializer.ModelSerializeException;
import org.citydb.io.citygml.serializer.ModelSerializer;
import org.citydb.io.citygml.writer.ModelSerializerHelper;
import org.citydb.model.common.Name;
import org.citydb.model.common.Namespaces;
import org.citydb.model.property.*;
import org.citygml4j.core.model.dynamizer.TimeValuePair;

public class TimeValuePairAdapter implements ModelBuilder<TimeValuePair, Attribute>, ModelSerializer<Attribute, TimeValuePair> {

    @Override
    public void build(TimeValuePair source, Attribute target, ModelBuilderHelper helper) throws ModelBuildException {
        if (source.getTimestamp() != null) {
            helper.addAttribute(Name.of("timestamp", Namespaces.DYNAMIZER), source.getTimestamp(), target,
                    TimePositionAdapter.class);
        }

        if (source.isSetIntValue()) {
            target.addProperty(Attribute.of(Name.of("intValue", Namespaces.DYNAMIZER), DataType.INTEGER)
                    .setIntValue(source.getIntValue()));
        } else if (source.isSetDoubleValue()) {
            target.addProperty(Attribute.of(Name.of("doubleValue", Namespaces.DYNAMIZER), DataType.DOUBLE)
                    .setDoubleValue(source.getDoubleValue()));
        } else if (source.isSetStringValue()) {
            target.addProperty(Attribute.of(Name.of("stringValue", Namespaces.DYNAMIZER), DataType.STRING)
                    .setStringValue(source.getStringValue()));
        } else if (source.isSetGeometryValue()) {
            helper.addGeometry(Name.of("geometryValue", Namespaces.DYNAMIZER),
                    source.getGeometryValue(), Lod.NONE, target);
        } else if (source.isSetUriValue()) {
            target.addProperty(Attribute.of(Name.of("uriValue", Namespaces.DYNAMIZER), DataType.URI)
                    .setURI(source.getUriValue()));
        } else if (source.isSetBoolValue()) {
            target.addProperty(Attribute.of(Name.of("boolValue", Namespaces.DYNAMIZER), DataType.BOOLEAN)
                    .setIntValue(source.getBoolValue() ? 1 : 0));
        } else if (source.isSetImplicitGeometryValue()) {
            helper.addImplicitGeometry(Name.of("implicitGeometryValue", Namespaces.DYNAMIZER),
                    source.getImplicitGeometryValue(), Lod.NONE, target);
        } else if (source.isSetAppearanceValue()) {
            helper.addAppearance(Name.of("appearanceValue", Namespaces.DYNAMIZER),
                    source.getAppearanceValue(), target);
        }

        target.setDataType(DataType.TIME_VALUE_PAIR);
    }

    @Override
    public TimeValuePair createObject(Attribute source) throws ModelSerializeException {
        return new TimeValuePair();
    }

    @Override
    public void serialize(Attribute source, TimeValuePair target, ModelSerializerHelper helper) throws ModelSerializeException {
        Attribute timestamp = source.getProperties()
                .getFirst(Name.of("timestamp", Namespaces.DYNAMIZER), Attribute.class)
                .orElse(null);
        if (timestamp != null) {
            target.setTimestamp(helper.getAttribute(timestamp, TimePositionAdapter.class));
        }

        source.getProperties().getFirst(Name.of("intValue", Namespaces.DYNAMIZER), Attribute.class)
                .flatMap(Attribute::getIntValue)
                .ifPresent(value -> target.setIntValue(value.intValue()));

        source.getProperties().getFirst(Name.of("doubleValue", Namespaces.DYNAMIZER), Attribute.class)
                .flatMap(Attribute::getDoubleValue)
                .ifPresent(target::setDoubleValue);

        source.getProperties().getFirst(Name.of("stringValue", Namespaces.DYNAMIZER), Attribute.class)
                .flatMap(Attribute::getStringValue)
                .ifPresent(target::setStringValue);

        GeometryProperty geometryValue = source.getProperties()
                .getFirst(Name.of("geometryValue", Namespaces.DYNAMIZER), GeometryProperty.class)
                .orElse(null);
        if (geometryValue != null) {
            target.setGeometryValue(helper.getGeometryProperty(geometryValue, GeometryPropertyAdapter.class));
        }

        source.getProperties().getFirst(Name.of("uriValue", Namespaces.DYNAMIZER), Attribute.class)
                .flatMap(Attribute::getURI)
                .ifPresent(target::setUriValue);

        source.getProperties().getFirst(Name.of("boolValue", Namespaces.DYNAMIZER), Attribute.class)
                .flatMap(Attribute::getIntValue)
                .ifPresent(value -> target.setBoolValue(value == 1));

        ImplicitGeometryProperty implicitGeometryValue = source.getProperties()
                .getFirst(Name.of("implicitGeometryValue", Namespaces.DYNAMIZER), ImplicitGeometryProperty.class)
                .orElse(null);
        if (implicitGeometryValue != null) {
            target.setImplicitGeometryValue(helper.getImplicitGeometryProperty(implicitGeometryValue,
                    ImplicitGeometryPropertyAdapter.class));
        }

        AppearanceProperty appearanceValue = source.getProperties()
                .getFirst(Name.of("appearanceValue", Namespaces.DYNAMIZER), AppearanceProperty.class)
                .orElse(null);
        if (appearanceValue != null) {
            target.setAppearanceValue(helper.getAppearanceProperty(appearanceValue,
                    AbstractAppearancePropertyAdapter.class));
        }
    }
}
