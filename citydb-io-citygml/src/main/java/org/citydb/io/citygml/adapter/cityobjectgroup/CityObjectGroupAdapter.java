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

package org.citydb.io.citygml.adapter.cityobjectgroup;

import org.citydb.io.citygml.adapter.core.AbstractCityObjectReferenceAdapter;
import org.citydb.io.citygml.adapter.core.AbstractLogicalSpaceAdapter;
import org.citydb.io.citygml.adapter.geometry.builder.Lod;
import org.citydb.io.citygml.adapter.geometry.serializer.GeometryPropertyAdapter;
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
import org.citydb.model.property.FeatureProperty;
import org.citydb.model.property.GeometryProperty;
import org.citygml4j.core.model.CityGMLVersion;
import org.citygml4j.core.model.cityobjectgroup.CityObjectGroup;
import org.citygml4j.core.model.cityobjectgroup.Role;
import org.citygml4j.core.model.cityobjectgroup.RoleProperty;
import org.citygml4j.core.model.deprecated.cityobjectgroup.DeprecatedPropertiesOfCityObjectGroup;

@DatabaseType(name = "CityObjectGroup", namespace = Namespaces.CITY_OBJECT_GROUP)
public class CityObjectGroupAdapter extends AbstractLogicalSpaceAdapter<CityObjectGroup> {

    @Override
    public Feature createModel(CityObjectGroup source) throws ModelBuildException {
        return Feature.of(FeatureType.CITY_OBJECT_GROUP);
    }

    @Override
    public void build(CityObjectGroup source, Feature target, ModelBuilderHelper helper) throws ModelBuildException {
        super.build(source, target, helper);
        helper.addStandardObjectClassifiers(source, target, Namespaces.CITY_OBJECT_GROUP);

        if (source.isSetGroupMembers()) {
            for (RoleProperty property : source.getGroupMembers()) {
                if (property != null) {
                    helper.addAttribute(Name.of("groupMember", Namespaces.CITY_OBJECT_GROUP), property.getObject(),
                            target, RoleAdapter.class);
                }
            }
        }

        if (source.getGroupParent() != null) {
            helper.addFeature(Name.of("parent", Namespaces.CITY_OBJECT_GROUP), source.getGroupParent(), target);
        }

        if (source.hasDeprecatedProperties()) {
            DeprecatedPropertiesOfCityObjectGroup properties = source.getDeprecatedProperties();
            if (properties.getGeometry() != null) {
                helper.addGeometry(Name.of("geometry", Namespaces.DEPRECATED), properties.getGeometry(),
                        Lod.NONE, target);
            }
        }
    }

    @Override
    public CityObjectGroup createObject(Feature source) throws ModelSerializeException {
        return new CityObjectGroup();
    }

    @Override
    public void serialize(Feature source, CityObjectGroup target, ModelSerializerHelper helper) throws ModelSerializeException {
        super.serialize(source, target, helper);
        helper.addStandardObjectClassifiers(source, target, Namespaces.CITY_OBJECT_GROUP);

        for (Attribute attribute : source.getAttributes().get(Name.of("groupMember", Namespaces.CITY_OBJECT_GROUP))) {
            Role role = helper.getAttribute(attribute, RoleAdapter.class);
            if (role.getGroupMember() != null) {
                target.getGroupMembers().add(new RoleProperty(role));
            }
        }

        FeatureProperty parent = source.getFeatures()
                .getFirst(Name.of("parent", Namespaces.CITY_OBJECT_GROUP))
                .orElse(null);
        if (parent != null) {
            target.setGroupParent(helper.getObjectProperty(parent, AbstractCityObjectReferenceAdapter.class));
        }
    }

    @Override
    public void postSerialize(Feature source, CityObjectGroup target, ModelSerializerHelper helper) throws ModelSerializeException {
        super.postSerialize(source, target, helper);

        if (helper.getCityGMLVersion() == CityGMLVersion.v2_0
                && source.getGeometries().containsNamespace(Namespaces.DEPRECATED)) {
            GeometryProperty geometry = source.getGeometries()
                    .getFirst(Name.of("geometry", Namespaces.DEPRECATED))
                    .orElse(null);
            if (geometry != null) {
                target.getDeprecatedProperties().setGeometry(
                        helper.getGeometryProperty(geometry, GeometryPropertyAdapter.class));
            }
        }
    }
}
