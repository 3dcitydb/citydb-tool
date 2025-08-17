/*
 * citydb-tool - Command-line tool for the 3D City Database
 * https://www.3dcitydb.org/
 *
 * Copyright 2022-2025
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
            helper.getOrCreateBuilder(TimePositionAdapter.class).build(source.getTimestamp(), target, helper);
        }

        if (source.isSetIntValue()) {
            target.setIntValue(source.getIntValue())
                    .setDataType(DataType.TIME_INTEGER);
        } else if (source.isSetDoubleValue()) {
            target.setDoubleValue(source.getDoubleValue())
                    .setDataType(DataType.TIME_DOUBLE);
        } else if (source.isSetStringValue()) {
            target.setStringValue(source.getStringValue())
                    .setDataType(DataType.TIME_STRING);
        } else if (source.isSetGeometryValue()) {
            helper.addGeometry(Name.of("value", Namespaces.DYNAMIZER),
                    source.getGeometryValue(), Lod.NONE, target.setDataType(DataType.TIME_GEOMETRY));
        } else if (source.isSetUriValue()) {
            target.setURI(source.getUriValue())
                    .setDataType(DataType.TIME_URI);
        } else if (source.isSetBoolValue()) {
            target.setIntValue(source.getBoolValue() ? 1 : 0)
                    .setDataType(DataType.TIME_BOOLEAN);
        } else if (source.isSetImplicitGeometryValue()) {
            helper.addImplicitGeometry(Name.of("value", Namespaces.DYNAMIZER),
                    source.getImplicitGeometryValue(), Lod.NONE, target.setDataType(DataType.TIME_IMPLICIT_GEOMETRY));
        } else if (source.isSetAppearanceValue()) {
            helper.addAppearance(Name.of("value", Namespaces.DYNAMIZER),
                    source.getAppearanceValue(), target.setDataType(DataType.TIME_APPEARANCE));
        }
    }

    @Override
    public TimeValuePair createObject(Attribute source) throws ModelSerializeException {
        return new TimeValuePair();
    }

    @Override
    public void serialize(Attribute source, TimeValuePair target, ModelSerializerHelper helper) throws ModelSerializeException {
        target.setTimestamp(helper.getAttribute(source, TimePositionAdapter.class));

        DataType dataType = DataType.of(source.getDataType().orElse(null));
        if (dataType == DataType.TIME_INTEGER) {
            source.getIntValue().ifPresent(value -> target.setIntValue(value.intValue()));
        } else if (dataType == DataType.TIME_DOUBLE) {
            source.getDoubleValue().ifPresent(target::setDoubleValue);
        } else if (dataType == DataType.TIME_STRING) {
            source.getStringValue().ifPresent(target::setStringValue);
        } else if (dataType == DataType.TIME_GEOMETRY) {
            GeometryProperty geometryValue = source.getProperties()
                    .getFirst(Name.of("value", Namespaces.DYNAMIZER), GeometryProperty.class)
                    .orElse(null);
            if (geometryValue != null) {
                target.setGeometryValue(helper.getGeometryProperty(geometryValue, GeometryPropertyAdapter.class));
            }
        } else if (dataType == DataType.TIME_URI) {
            source.getURI().ifPresent(target::setUriValue);
        } else if (dataType == DataType.TIME_BOOLEAN) {
            source.getIntValue().ifPresent(value -> target.setBoolValue(value == 1));
        } else if (dataType == DataType.TIME_IMPLICIT_GEOMETRY) {
            ImplicitGeometryProperty implicitGeometryValue = source.getProperties()
                    .getFirst(Name.of("value", Namespaces.DYNAMIZER), ImplicitGeometryProperty.class)
                    .orElse(null);
            if (implicitGeometryValue != null) {
                target.setImplicitGeometryValue(helper.getImplicitGeometryProperty(implicitGeometryValue,
                        ImplicitGeometryPropertyAdapter.class));
            }
        } else if (dataType == DataType.TIME_APPEARANCE) {
            AppearanceProperty appearanceValue = source.getProperties()
                    .getFirst(Name.of("value", Namespaces.DYNAMIZER), AppearanceProperty.class)
                    .orElse(null);
            if (appearanceValue != null) {
                target.setAppearanceValue(helper.getAppearanceProperty(appearanceValue,
                        AbstractAppearancePropertyAdapter.class));
            }
        }
    }
}
