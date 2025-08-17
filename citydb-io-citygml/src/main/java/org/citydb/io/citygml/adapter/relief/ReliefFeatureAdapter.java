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

package org.citydb.io.citygml.adapter.relief;

import org.citydb.io.citygml.adapter.core.AbstractSpaceBoundaryAdapter;
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
import org.citygml4j.core.model.relief.AbstractReliefComponentProperty;
import org.citygml4j.core.model.relief.ReliefFeature;

@DatabaseType(name = "ReliefFeature", namespace = Namespaces.RELIEF)
public class ReliefFeatureAdapter extends AbstractSpaceBoundaryAdapter<ReliefFeature> {

    @Override
    public Feature createModel(ReliefFeature source) throws ModelBuildException {
        return Feature.of(FeatureType.RELIEF_FEATURE);
    }

    @Override
    public void build(ReliefFeature source, Feature target, ModelBuilderHelper helper) throws ModelBuildException {
        super.build(source, target, helper);

        target.addAttribute(Attribute.of(Name.of("lod", Namespaces.RELIEF), DataType.INTEGER)
                .setIntValue(source.getLod()));

        if (source.isSetReliefComponents()) {
            for (AbstractReliefComponentProperty property : source.getReliefComponents()) {
                helper.addContainedFeature(Name.of("reliefComponent", Namespaces.RELIEF), property, target);
            }
        }
    }

    @Override
    public ReliefFeature createObject(Feature source) throws ModelSerializeException {
        return new ReliefFeature();
    }

    @Override
    public void serialize(Feature source, ReliefFeature target, ModelSerializerHelper helper) throws ModelSerializeException {
        super.serialize(source, target, helper);

        source.getAttributes().getFirst(Name.of("lod", Namespaces.RELIEF))
                .flatMap(Attribute::getIntValue)
                .ifPresent(value -> target.setLod(value.intValue()));

        for (FeatureProperty property : source.getFeatures().get(Name.of("reliefComponent", Namespaces.RELIEF))) {
            target.getReliefComponents().add(helper.getObjectProperty(property,
                    AbstractReliefComponentPropertyAdapter.class));
        }
    }
}
