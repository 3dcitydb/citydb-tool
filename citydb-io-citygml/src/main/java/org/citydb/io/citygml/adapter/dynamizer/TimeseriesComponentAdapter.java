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

import org.citydb.io.citygml.adapter.gml.TimeDurationAdapter;
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
import org.citygml4j.core.model.dynamizer.TimeseriesComponent;

public class TimeseriesComponentAdapter implements ModelBuilder<TimeseriesComponent, Attribute>, ModelSerializer<Attribute, TimeseriesComponent> {

    @Override
    public void build(TimeseriesComponent source, Attribute target, ModelBuilderHelper helper) throws ModelBuildException {
        if (source.getRepetitions() != null) {
            target.addProperty(Attribute.of(Name.of("repetitions", Namespaces.DYNAMIZER), DataType.INTEGER)
                    .setIntValue(source.getRepetitions()));
        }

        helper.addAttribute(Name.of("additionalGap", Namespaces.DYNAMIZER), source.getAdditionalGap(), target,
                TimeDurationAdapter.class);

        helper.addContainedFeature(Name.of("timeseries", Namespaces.DYNAMIZER), source.getTimeseries(), target);
        target.setDataType(DataType.TIMESERIES_COMPONENT);
    }

    @Override
    public TimeseriesComponent createObject(Attribute source) throws ModelSerializeException {
        return new TimeseriesComponent();
    }

    @Override
    public void serialize(Attribute source, TimeseriesComponent target, ModelSerializerHelper helper) throws ModelSerializeException {
        source.getProperties().getFirst(Name.of("repetitions", Namespaces.DYNAMIZER), Attribute.class)
                .flatMap(Attribute::getIntValue)
                .ifPresent(value -> target.setRepetitions(value.intValue()));

        Attribute additionalGap = source.getProperties()
                .getFirst(Name.of("additionalGap", Namespaces.DYNAMIZER), Attribute.class)
                .orElse(null);
        if (additionalGap != null) {
            target.setAdditionalGap(helper.getAttribute(additionalGap, TimeDurationAdapter.class));
        }

        FeatureProperty timeseries = source.getProperties()
                .getFirst(Name.of("timeseries", Namespaces.DYNAMIZER), FeatureProperty.class)
                .orElse(null);
        if (timeseries != null) {
            target.setTimeseries(helper.getObjectProperty(timeseries, AbstractTimeseriesPropertyAdapter.class));
        }
    }
}
