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
import org.citygml4j.core.model.dynamizer.GenericTimeseries;
import org.citygml4j.core.model.dynamizer.TimeValuePairProperty;
import org.citygml4j.core.model.dynamizer.TimeseriesValue;

@DatabaseType(name = "GenericTimeseries", namespace = Namespaces.DYNAMIZER)
public class GenericTimeseriesAdapter extends AbstractAtomicTimeseriesAdapter<GenericTimeseries> {

    @Override
    public Feature createModel(GenericTimeseries source) throws ModelBuildException {
        return Feature.of(FeatureType.GENERIC_TIMESERIES);
    }

    @Override
    public void build(GenericTimeseries source, Feature target, ModelBuilderHelper helper) throws ModelBuildException {
        super.build(source, target, helper);

        if (source.getValueType() != null) {
            target.addProperty(Attribute.of(Name.of("valueType", Namespaces.DYNAMIZER), DataType.STRING)
                    .setStringValue(source.getValueType().toValue()));
        }

        if (source.isSetTimeValuePairs()) {
            for (TimeValuePairProperty property : source.getTimeValuePairs()) {
                if (property != null) {
                    helper.addAttribute(Name.of("timeValuePair", Namespaces.DYNAMIZER), property.getObject(), target,
                            TimeValuePairAdapter.class);
                }
            }
        }
    }

    @Override
    public GenericTimeseries createObject(Feature source) throws ModelSerializeException {
        return new GenericTimeseries();
    }

    @Override
    public void serialize(Feature source, GenericTimeseries target, ModelSerializerHelper helper) throws ModelSerializeException {
        super.serialize(source, target, helper);

        source.getAttributes().getFirst(Name.of("valueType", Namespaces.DYNAMIZER))
                .flatMap(Attribute::getStringValue)
                .ifPresent(value -> target.setValueType(TimeseriesValue.fromValue(value)));

        for (Attribute attribute : source.getAttributes().get(Name.of("timeValuePair", Namespaces.DYNAMIZER))) {
            target.getTimeValuePairs().add(new TimeValuePairProperty(
                    helper.getAttribute(attribute, TimeValuePairAdapter.class)));
        }
    }
}
