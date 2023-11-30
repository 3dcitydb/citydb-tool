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

package org.citydb.io.citygml.adapter.core;

import org.citydb.io.citygml.adapter.appearance.AbstractAppearancePropertyAdapter;
import org.citydb.io.citygml.builder.ModelBuildException;
import org.citydb.io.citygml.reader.ModelBuilderHelper;
import org.citydb.io.citygml.serializer.ModelSerializeException;
import org.citydb.io.citygml.writer.ModelSerializerHelper;
import org.citydb.model.common.Name;
import org.citydb.model.common.Namespaces;
import org.citydb.model.feature.Feature;
import org.citydb.model.property.AppearanceProperty;
import org.citydb.model.property.Attribute;
import org.citydb.model.property.DataType;
import org.citydb.model.property.FeatureProperty;
import org.citygml4j.core.model.core.*;

public abstract class AbstractCityObjectAdapter<T extends AbstractCityObject> extends AbstractFeatureWithLifespanAdapter<T> {

    @Override
    public void build(T source, Feature target, ModelBuilderHelper helper) throws ModelBuildException {
        super.build(source, target, helper);

        if (source.isSetExternalReferences()) {
            for (ExternalReferenceProperty property : source.getExternalReferences()) {
                if (property != null) {
                    helper.addAttribute(Name.of("externalReference", Namespaces.CORE), property.getObject(),
                            target, ExternalReferenceAdapter.class);
                }
            }
        }

        if (source.isSetGeneralizesTo()) {
            for (AbstractCityObjectReference reference : source.getGeneralizesTo()) {
                helper.addFeature(Name.of("generalizesTo", Namespaces.CORE), reference, target);
            }
        }

        if (source.getRelativeToTerrain() != null) {
            target.addAttribute(Attribute.of(Name.of("relativeToTerrain", Namespaces.CORE), DataType.STRING)
                    .setStringValue(source.getRelativeToTerrain().toValue()));
        }

        if (source.getRelativeToWater() != null) {
            target.addAttribute(Attribute.of(Name.of("relativeToWater", Namespaces.CORE), DataType.STRING)
                    .setStringValue(source.getRelativeToWater().toValue()));
        }

        if (source.isSetRelatedTo()) {
            for (CityObjectRelationProperty property : source.getRelatedTo()) {
                if (property != null) {
                    helper.addAttribute(Name.of("relatedTo", Namespaces.CORE), property.getObject(), target,
                            CityObjectRelationAdapter.class);
                }
            }
        }

        if (source.isSetAppearances()) {
            for (AbstractAppearanceProperty property : source.getAppearances()) {
                helper.addAppearance(Name.of("appearance", Namespaces.CORE), property, target);
            }
        }

        if (source.isSetGenericAttributes()) {
            for (AbstractGenericAttributeProperty property : source.getGenericAttributes()) {
                if (property != null) {
                    helper.addAttribute(property.getObject(), target);
                }
            }
        }

        if (source.isSetDynamizers()) {
            for (AbstractDynamizerProperty property : source.getDynamizers()) {
                helper.addFeature(Name.of("dynamizer", Namespaces.CORE), property, target);
            }
        }
    }

    @Override
    public void serialize(Feature source, T target, ModelSerializerHelper helper) throws ModelSerializeException {
        super.serialize(source, target, helper);

        for (Attribute attribute : source.getAttributes().get(Name.of("externalReference", Namespaces.CORE))) {
            target.getExternalReferences().add(new ExternalReferenceProperty(
                    helper.getAttribute(attribute, ExternalReferenceAdapter.class)));
        }

        for (FeatureProperty property : source.getFeatures().get(Name.of("generalizesTo", Namespaces.CORE))) {
            target.getGeneralizesTo().add(helper.getObjectProperty(property,
                    AbstractCityObjectReferenceAdapter.class));
        }

        source.getAttributes().getFirst(Name.of("relativeToTerrain", Namespaces.CORE))
                .flatMap(Attribute::getStringValue)
                .ifPresent(value -> target.setRelativeToTerrain(RelativeToTerrain.fromValue(value)));

        source.getAttributes().getFirst(Name.of("relativeToWater", Namespaces.CORE))
                .flatMap(Attribute::getStringValue)
                .ifPresent(value -> target.setRelativeToWater(RelativeToWater.fromValue(value)));

        for (Attribute attribute : source.getAttributes().get(Name.of("relatedTo", Namespaces.CORE))) {
            CityObjectRelation relation = helper.getAttribute(attribute, CityObjectRelationAdapter.class);
            if (relation.getRelatedTo() != null) {
                target.getRelatedTo().add(new CityObjectRelationProperty(relation));
            }
        }

        if (source.hasAppearances()) {
            for (AppearanceProperty property : source.getAppearances().getAll()) {
                target.getAppearances().add(
                        helper.getAppearanceProperty(property, AbstractAppearancePropertyAdapter.class));
            }
        }

        for (Attribute attribute : source.getAttributes().getByNamespace(Namespaces.GENERICS)) {
            AbstractGenericAttribute<?> genericAttribute = helper.getGenericAttribute(attribute);
            if (genericAttribute != null) {
                target.getGenericAttributes().add(new AbstractGenericAttributeProperty(genericAttribute));
            }
        }

        for (FeatureProperty property : source.getFeatures().get(Name.of("dynamizer", Namespaces.CORE))) {
            target.getDynamizers().add(helper.getObjectProperty(property, AbstractDynamizerPropertyAdapter.class));
        }
    }
}
