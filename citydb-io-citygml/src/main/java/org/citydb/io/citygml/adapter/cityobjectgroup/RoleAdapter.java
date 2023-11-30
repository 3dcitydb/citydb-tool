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
import org.citygml4j.core.model.cityobjectgroup.Role;

public class RoleAdapter implements ModelBuilder<Role, Attribute>, ModelSerializer<Attribute, Role> {

    @Override
    public void build(Role source, Attribute target, ModelBuilderHelper helper) throws ModelBuildException {
        helper.addFeature(Name.of("groupMember", Namespaces.CITY_OBJECT_GROUP), source.getGroupMember(), target);

        if (source.getRole() != null) {
            target.addProperty(Attribute.of(Name.of("role", Namespaces.CITY_OBJECT_GROUP), DataType.STRING)
                    .setStringValue(source.getRole()));
        }

        target.setDataType(DataType.ROLE);
    }

    @Override
    public Role createObject(Attribute source) throws ModelSerializeException {
        return new Role();
    }

    @Override
    public void serialize(Attribute source, Role target, ModelSerializerHelper helper) throws ModelSerializeException {
        FeatureProperty groupMember = source.getProperties()
                .getFirst(Name.of("groupMember", Namespaces.CITY_OBJECT_GROUP), FeatureProperty.class)
                .orElse(null);
        if (groupMember != null) {
            target.setGroupMember(helper.getObjectProperty(groupMember, AbstractCityObjectReferenceAdapter.class));
        }

        source.getProperties().getFirst(Name.of("role", Namespaces.CITY_OBJECT_GROUP), Attribute.class)
                .flatMap(Attribute::getStringValue)
                .ifPresent(target::setRole);
    }
}
