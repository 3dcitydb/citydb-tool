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

import org.citydb.io.citygml.adapter.core.AbstractFeatureWithLifespanAdapter;
import org.citydb.io.citygml.adapter.gml.TimePositionAdapter;
import org.citydb.io.citygml.annotation.DatabaseType;
import org.citydb.io.citygml.builder.ModelBuildException;
import org.citydb.io.citygml.reader.ModelBuilderHelper;
import org.citydb.io.citygml.serializer.ModelSerializeException;
import org.citydb.io.citygml.writer.ModelSerializerHelper;
import org.citydb.model.common.Name;
import org.citydb.model.common.Namespaces;
import org.citydb.model.feature.Feature;
import org.citydb.model.feature.FeatureType;
import org.citydb.model.property.Attribute;
import org.citydb.model.property.DataType;
import org.citydb.model.property.FeatureProperty;
import org.citygml4j.core.model.dynamizer.Dynamizer;
import org.citygml4j.core.model.dynamizer.SensorConnectionProperty;

@DatabaseType(name = "Dynamizer", namespace = Namespaces.DYNAMIZER)
public class DynamizerAdapter extends AbstractFeatureWithLifespanAdapter<Dynamizer> {

    @Override
    public Feature createModel(Dynamizer source) throws ModelBuildException {
        return Feature.of(FeatureType.DYNAMIZER);
    }

    @Override
    public void build(Dynamizer source, Feature target, ModelBuilderHelper helper) throws ModelBuildException {
        super.build(source, target, helper);

        if (source.getAttributeRef() != null) {
            target.addAttribute(Attribute.of(Name.of("attributeRef", Namespaces.DYNAMIZER), DataType.STRING)
                    .setStringValue(source.getAttributeRef()));
        }

        if (source.getStartTime() != null) {
            helper.addAttribute(Name.of("startTime", Namespaces.DYNAMIZER), source.getStartTime(), target,
                    TimePositionAdapter.class);
        }

        if (source.getEndTime() != null) {
            helper.addAttribute(Name.of("endTime", Namespaces.DYNAMIZER), source.getEndTime(), target,
                    TimePositionAdapter.class);
        }

        if (source.getDynamicData() != null) {
            helper.addContainedFeature(Name.of("dynamicData", Namespaces.DYNAMIZER), source.getDynamicData(), target);
        }

        if (source.getSensorConnection() != null && source.getSensorConnection().getObject() != null) {
            helper.addAttribute(Name.of("sensorConnection", Namespaces.DYNAMIZER),
                    source.getSensorConnection().getObject(), target, SensorConnectionAdapter.class);
        }
    }

    @Override
    public Dynamizer createObject(Feature source) throws ModelSerializeException {
        return new Dynamizer();
    }

    @Override
    public void serialize(Feature source, Dynamizer target, ModelSerializerHelper helper) throws ModelSerializeException {
        super.serialize(source, target, helper);

        source.getAttributes().getFirst(Name.of("attributeRef", Namespaces.DYNAMIZER))
                .flatMap(Attribute::getStringValue)
                .ifPresent(target::setAttributeRef);

        Attribute startTime = source.getAttributes()
                .getFirst(Name.of("startTime", Namespaces.DYNAMIZER))
                .orElse(null);
        if (startTime != null) {
            target.setStartTime(helper.getAttribute(startTime, TimePositionAdapter.class));
        }

        Attribute endTime = source.getAttributes()
                .getFirst(Name.of("endTime", Namespaces.DYNAMIZER))
                .orElse(null);
        if (endTime != null) {
            target.setEndTime(helper.getAttribute(endTime, TimePositionAdapter.class));
        }

        FeatureProperty dynamicData = source.getFeatures()
                .getFirst(Name.of("dynamicData", Namespaces.DYNAMIZER))
                .orElse(null);
        if (dynamicData != null) {
            target.setDynamicData(helper.getObjectProperty(dynamicData, AbstractTimeseriesPropertyAdapter.class));
        }

        Attribute sensorConnection = source.getAttributes()
                .getFirst(Name.of("sensorConnection", Namespaces.DYNAMIZER))
                .orElse(null);
        if (sensorConnection != null) {
            target.setSensorConnection(new SensorConnectionProperty(
                    helper.getAttribute(sensorConnection, SensorConnectionAdapter.class)));
        }
    }
}
