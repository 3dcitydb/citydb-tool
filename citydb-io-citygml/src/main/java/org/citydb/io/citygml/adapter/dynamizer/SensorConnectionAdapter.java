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

import org.citydb.io.citygml.adapter.core.AbstractCityObjectReferenceAdapter;
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
import org.citydb.model.property.FeatureProperty;
import org.citygml4j.core.model.dynamizer.SensorConnection;

public class SensorConnectionAdapter implements ModelBuilder<SensorConnection, Attribute>, ModelSerializer<Attribute, SensorConnection> {

    @Override
    public void build(SensorConnection source, Attribute target, ModelBuilderHelper helper) throws ModelBuildException {
        helper.addAttribute(Name.of("connectionType", Namespaces.DYNAMIZER), source.getConnectionType(), target,
                CodeAdapter.class);

        if (source.getObservationProperty() != null) {
            target.addProperty(Attribute.of(Name.of("observationProperty", Namespaces.DYNAMIZER), DataType.STRING)
                    .setStringValue(source.getObservationProperty()));
        }

        if (source.getUom() != null) {
            target.addProperty(Attribute.of(Name.of("uom", Namespaces.DYNAMIZER), DataType.STRING)
                    .setStringValue(source.getUom()));
        }

        if (source.getSensorID() != null) {
            target.addProperty(Attribute.of(Name.of("sensorID", Namespaces.DYNAMIZER), DataType.STRING)
                    .setStringValue(source.getSensorID()));
        }

        if (source.getSensorName() != null) {
            target.addProperty(Attribute.of(Name.of("sensorName", Namespaces.DYNAMIZER), DataType.STRING)
                    .setStringValue(source.getSensorName()));
        }

        if (source.getObservationID() != null) {
            target.addProperty(Attribute.of(Name.of("observationID", Namespaces.DYNAMIZER), DataType.STRING)
                    .setStringValue(source.getObservationID()));
        }

        if (source.getDatastreamID() != null) {
            target.addProperty(Attribute.of(Name.of("datastreamID", Namespaces.DYNAMIZER), DataType.STRING)
                    .setStringValue(source.getDatastreamID()));
        }

        if (source.getBaseURL() != null) {
            target.addProperty(Attribute.of(Name.of("baseURL", Namespaces.DYNAMIZER), DataType.URI)
                    .setURI(source.getBaseURL()));
        }

        helper.addAttribute(Name.of("authType", Namespaces.DYNAMIZER), source.getAuthType(), target,
                CodeAdapter.class);

        if (source.getMqttServer() != null) {
            target.addProperty(Attribute.of(Name.of("mqttServer", Namespaces.DYNAMIZER), DataType.STRING)
                    .setStringValue(source.getMqttServer()));
        }

        if (source.getMqttTopic() != null) {
            target.addProperty(Attribute.of(Name.of("mqttTopic", Namespaces.DYNAMIZER), DataType.STRING)
                    .setStringValue(source.getMqttTopic()));
        }

        if (source.getLinkToObservation() != null) {
            target.addProperty(Attribute.of(Name.of("linkToObservation", Namespaces.DYNAMIZER), DataType.STRING)
                    .setStringValue(source.getLinkToObservation()));
        }

        if (source.getLinkToSensorDescription() != null) {
            target.addProperty(Attribute.of(Name.of("linkToSensorDescription", Namespaces.DYNAMIZER), DataType.STRING)
                    .setStringValue(source.getLinkToSensorDescription()));
        }

        helper.addRelatedFeature(Name.of("sensorLocation", Namespaces.DYNAMIZER), source.getSensorLocation(), target);
        target.setDataType(DataType.SENSOR_CONNECTION);
    }

    @Override
    public SensorConnection createObject(Attribute source) throws ModelSerializeException {
        return new SensorConnection();
    }

    @Override
    public void serialize(Attribute source, SensorConnection target, ModelSerializerHelper helper) throws ModelSerializeException {
        Attribute connectionType = source.getProperties()
                .getFirst(Name.of("connectionType", Namespaces.DYNAMIZER), Attribute.class)
                .orElse(null);
        if (connectionType != null) {
            target.setConnectionType(helper.getAttribute(connectionType, CodeAdapter.class));
        }

        source.getProperties().getFirst(Name.of("observationProperty", Namespaces.DYNAMIZER), Attribute.class)
                .flatMap(Attribute::getStringValue)
                .ifPresent(target::setObservationProperty);

        source.getProperties().getFirst(Name.of("uom", Namespaces.DYNAMIZER), Attribute.class)
                .flatMap(Attribute::getStringValue)
                .ifPresent(target::setUom);

        source.getProperties().getFirst(Name.of("sensorID", Namespaces.DYNAMIZER), Attribute.class)
                .flatMap(Attribute::getStringValue)
                .ifPresent(target::setSensorID);

        source.getProperties().getFirst(Name.of("sensorName", Namespaces.DYNAMIZER), Attribute.class)
                .flatMap(Attribute::getStringValue)
                .ifPresent(target::setSensorName);

        source.getProperties().getFirst(Name.of("observationID", Namespaces.DYNAMIZER), Attribute.class)
                .flatMap(Attribute::getStringValue)
                .ifPresent(target::setObservationID);

        source.getProperties().getFirst(Name.of("datastreamID", Namespaces.DYNAMIZER), Attribute.class)
                .flatMap(Attribute::getStringValue)
                .ifPresent(target::setDatastreamID);

        source.getProperties().getFirst(Name.of("baseURL", Namespaces.DYNAMIZER), Attribute.class)
                .flatMap(Attribute::getURI)
                .ifPresent(target::setBaseURL);

        Attribute authType = source.getProperties()
                .getFirst(Name.of("authType", Namespaces.DYNAMIZER), Attribute.class)
                .orElse(null);
        if (authType != null) {
            target.setAuthType(helper.getAttribute(authType, CodeAdapter.class));
        }

        source.getProperties().getFirst(Name.of("mqttServer", Namespaces.DYNAMIZER), Attribute.class)
                .flatMap(Attribute::getStringValue)
                .ifPresent(target::setMqttServer);

        source.getProperties().getFirst(Name.of("mqttTopic", Namespaces.DYNAMIZER), Attribute.class)
                .flatMap(Attribute::getStringValue)
                .ifPresent(target::setMqttTopic);

        source.getProperties().getFirst(Name.of("linkToObservation", Namespaces.DYNAMIZER), Attribute.class)
                .flatMap(Attribute::getStringValue)
                .ifPresent(target::setLinkToObservation);

        source.getProperties().getFirst(Name.of("linkToSensorDescription", Namespaces.DYNAMIZER), Attribute.class)
                .flatMap(Attribute::getStringValue)
                .ifPresent(target::setLinkToSensorDescription);

        FeatureProperty sensorLocation = source.getProperties()
                .getFirst(Name.of("sensorLocation", Namespaces.DYNAMIZER), FeatureProperty.class)
                .orElse(null);
        if (sensorLocation != null) {
            target.setSensorLocation(helper.getObjectProperty(sensorLocation,
                    AbstractCityObjectReferenceAdapter.class));
        }
    }
}
