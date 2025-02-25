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

import org.citydb.io.citygml.adapter.core.AbstractFeatureAdapter;
import org.citydb.io.citygml.adapter.gml.TimePositionAdapter;
import org.citydb.io.citygml.builder.ModelBuildException;
import org.citydb.io.citygml.reader.ModelBuilderHelper;
import org.citydb.io.citygml.serializer.ModelSerializeException;
import org.citydb.io.citygml.writer.ModelSerializerHelper;
import org.citydb.model.common.Name;
import org.citydb.model.common.Namespaces;
import org.citydb.model.feature.Feature;
import org.citydb.model.property.Attribute;
import org.citygml4j.core.model.dynamizer.AbstractTimeseries;

public abstract class AbstractTimeseriesAdapter<T extends AbstractTimeseries> extends AbstractFeatureAdapter<T> {

    @Override
    public void build(T source, Feature target, ModelBuilderHelper helper) throws ModelBuildException {
        super.build(source, target, helper);

        if (source.getFirstTimestamp() != null) {
            helper.addAttribute(Name.of("firstTimestamp", Namespaces.DYNAMIZER), source.getFirstTimestamp(), target,
                    TimePositionAdapter.class);
        }

        if (source.getLastTimestamp() != null) {
            helper.addAttribute(Name.of("lastTimestamp", Namespaces.DYNAMIZER), source.getLastTimestamp(), target,
                    TimePositionAdapter.class);
        }
    }

    @Override
    public void serialize(Feature source, T target, ModelSerializerHelper helper) throws ModelSerializeException {
        super.serialize(source, target, helper);

        Attribute firstTimestamp = source.getAttributes()
                .getFirst(Name.of("firstTimestamp", Namespaces.DYNAMIZER))
                .orElse(null);
        if (firstTimestamp != null) {
            target.setFirstTimestamp(helper.getAttribute(firstTimestamp, TimePositionAdapter.class));
        }

        Attribute lastTimestamp = source.getAttributes()
                .getFirst(Name.of("lastTimestamp", Namespaces.DYNAMIZER))
                .orElse(null);
        if (lastTimestamp != null) {
            target.setLastTimestamp(helper.getAttribute(lastTimestamp, TimePositionAdapter.class));
        }
    }
}
