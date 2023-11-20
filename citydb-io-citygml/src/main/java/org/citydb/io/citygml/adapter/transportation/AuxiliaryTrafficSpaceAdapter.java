/*
 * citydb-tool - Command-line tool for the 3D City Database
 * https://www.3dcitydb.org/
 *
 * Copyright 2022-2023
 * Virtual City Systems, Germany
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

package org.citydb.io.citygml.adapter.transportation;

import org.citydb.io.citygml.adapter.core.AbstractUnoccupiedSpaceAdapter;
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
import org.citygml4j.core.model.transportation.AuxiliaryTrafficSpace;
import org.citygml4j.core.model.transportation.GranularityValue;

@DatabaseType(name = "AuxiliaryTrafficSpace", namespace = Namespaces.TRANSPORTATION)
public class AuxiliaryTrafficSpaceAdapter extends AbstractUnoccupiedSpaceAdapter<AuxiliaryTrafficSpace> {

    @Override
    public Feature createModel(AuxiliaryTrafficSpace source) throws ModelBuildException {
        return Feature.of(FeatureType.AUXILIARY_TRAFFIC_SPACE);
    }

    @Override
    public void build(AuxiliaryTrafficSpace source, Feature target, ModelBuilderHelper helper) throws ModelBuildException {
        super.build(source, target, helper);
        helper.addStandardObjectClassifiers(source, target, Namespaces.TRANSPORTATION);

        if (source.getGranularity() != null) {
            target.addAttribute(Attribute.of(Name.of("granularity", Namespaces.TRANSPORTATION), DataType.STRING)
                    .setStringValue(source.getGranularity().toValue()));
        }
    }

    @Override
    public AuxiliaryTrafficSpace createObject(Feature source) throws ModelSerializeException {
        return new AuxiliaryTrafficSpace();
    }

    @Override
    public void serialize(Feature source, AuxiliaryTrafficSpace target, ModelSerializerHelper helper) throws ModelSerializeException {
        super.serialize(source, target, helper);
        helper.addStandardObjectClassifiers(source, target, Namespaces.TRANSPORTATION);

        source.getAttributes().getFirst(Name.of("granularity", Namespaces.TRANSPORTATION))
                .flatMap(Attribute::getStringValue)
                .ifPresent(value -> target.setGranularity(GranularityValue.fromValue(value)));
    }
}
