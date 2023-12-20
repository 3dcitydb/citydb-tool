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

package org.citydb.io.citygml.adapter.construction;

import org.citydb.io.citygml.adapter.core.AbstractOccupiedSpaceAdapter;
import org.citydb.io.citygml.builder.ModelBuildException;
import org.citydb.io.citygml.reader.ModelBuilderHelper;
import org.citydb.io.citygml.serializer.ModelSerializeException;
import org.citydb.io.citygml.writer.ModelSerializerHelper;
import org.citydb.model.common.Name;
import org.citydb.model.common.Namespaces;
import org.citydb.model.feature.Feature;
import org.citydb.model.property.Attribute;
import org.citydb.model.property.DataType;
import org.citydb.model.property.FeatureProperty;
import org.citygml4j.core.model.construction.AbstractConstructiveElement;
import org.citygml4j.core.model.construction.AbstractFillingElementProperty;

public abstract class AbstractConstructiveElementAdapter<T extends AbstractConstructiveElement> extends AbstractOccupiedSpaceAdapter<T> {

    @Override
    public void build(T source, Feature target, ModelBuilderHelper helper) throws ModelBuildException {
        super.build(source, target, helper);

        if (source.isSetIsStructuralElement()) {
            target.addAttribute(Attribute.of(Name.of("isStructuralElement", Namespaces.CONSTRUCTION), DataType.BOOLEAN)
                    .setIntValue(source.getIsStructuralElement() ? 1 : 0));
        }

        if (source.isSetFillings()) {
            for (AbstractFillingElementProperty property : source.getFillings()) {
                helper.addContainedFeature(Name.of("filling", Namespaces.CONSTRUCTION), property, target);
            }
        }
    }

    @Override
    public void serialize(Feature source, T target, ModelSerializerHelper helper) throws ModelSerializeException {
        super.serialize(source, target, helper);

        source.getAttributes().getFirst(Name.of("isStructuralElement", Namespaces.CONSTRUCTION))
                .flatMap(Attribute::getIntValue)
                .ifPresent(value -> target.setIsStructuralElement(value == 1));

        for (FeatureProperty property : source.getFeatures().get(Name.of("filling", Namespaces.CONSTRUCTION))) {
            target.getFillings().add(helper.getObjectProperty(property, AbstractFillingElementPropertyAdapter.class));
        }
    }
}
