/*
 * citydb-tool - Command-line tool for the 3D City Database
 * https://www.3dcitydb.org/
 *
 * Copyright 2022-2024
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
import org.citygml4j.core.model.core.AbstractGenericAttribute;
import org.citygml4j.core.model.core.AbstractGenericAttributeProperty;
import org.citygml4j.core.model.core.CityObjectRelation;

public class CityObjectRelationAdapter implements ModelBuilder<CityObjectRelation, Attribute>, ModelSerializer<Attribute, CityObjectRelation> {

    @Override
    public void build(CityObjectRelation source, Attribute target, ModelBuilderHelper helper) throws ModelBuildException {
        helper.addRelatedFeature(Name.of("relatedTo", Namespaces.CORE), source.getRelatedTo(), target);
        helper.addAttribute(Name.of("relationType", Namespaces.CORE), source.getRelationType(), target,
                CodeAdapter.class);

        if (source.isSetGenericAttributes()) {
            for (AbstractGenericAttributeProperty property : source.getGenericAttributes()) {
                if (property.getObject() != null) {
                    helper.addAttribute(property.getObject(), target);
                }
            }
        }

        target.setDataType(DataType.CITY_OBJECT_RELATION);
    }

    @Override
    public CityObjectRelation createObject(Attribute source) throws ModelSerializeException {
        return new CityObjectRelation();
    }

    @Override
    public void serialize(Attribute source, CityObjectRelation target, ModelSerializerHelper helper) throws ModelSerializeException {
        FeatureProperty relatedTo = source.getProperties()
                .getFirst(Name.of("relatedTo", Namespaces.CORE), FeatureProperty.class)
                .orElse(null);
        if (relatedTo != null) {
            target.setRelatedTo(helper.getObjectProperty(relatedTo, AbstractCityObjectReferenceAdapter.class));
        }

        Attribute relationType = source.getProperties()
                .getFirst(Name.of("relationType", Namespaces.CORE), Attribute.class)
                .orElse(null);
        if (relationType != null) {
            target.setRelationType(helper.getAttribute(relationType, CodeAdapter.class));
        }

        for (Attribute attribute : source.getProperties().getByNamespace(Namespaces.GENERICS, Attribute.class)) {
            AbstractGenericAttribute<?> genericAttribute = helper.getGenericAttribute(attribute);
            if (genericAttribute != null) {
                target.getGenericAttributes().add(new AbstractGenericAttributeProperty(genericAttribute));
            }
        }
    }
}
