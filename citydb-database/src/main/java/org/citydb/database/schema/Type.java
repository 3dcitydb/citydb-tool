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

package org.citydb.database.schema;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.citydb.model.common.Name;

import java.util.*;

public abstract class Type<T extends Type<T>> extends SchemaElement {
    final int id;
    final String identifier;
    final Table table;
    final boolean isAbstract;
    final Integer superTypeId;
    final Map<Name, Property> properties;
    final Join join;
    final JoinTable joinTable;
    T superType;

    Type(int id, String identifier, Name name, Table table, boolean isAbstract, Integer superTypeId,
         Map<Name, Property> properties, Join join, JoinTable joinTable) {
        super(name);
        this.id = id;
        this.identifier = identifier;
        this.table = table;
        this.isAbstract = isAbstract;
        this.superTypeId = superTypeId;
        this.properties = properties;
        this.join = join;
        this.joinTable = joinTable;
    }

    static Map<Name, Property> buildProperties(JSONArray propertiesArray) throws SchemaException {
        Map<Name, Property> properties = null;
        if (!propertiesArray.isEmpty()) {
            properties = new LinkedHashMap<>();
            for (Object item : propertiesArray) {
                if (item instanceof JSONObject propertyObject) {
                    Property property = Property.of(propertyObject);
                    properties.put(property.getName(), property);
                }
            }

            if (properties.size() != propertiesArray.size()) {
                throw new SchemaException("The properties array contains invalid properties.");
            }
        }

        return properties;
    }

    abstract T self();

    public int getId() {
        return id;
    }

    String getIdentifier() {
        return identifier;
    }

    public Table getTable() {
        return table;
    }

    public boolean isAbstract() {
        return isAbstract;
    }

    public Optional<T> getSuperType() {
        return Optional.ofNullable(superType);
    }

    public Map<Name, Property> getProperties() {
        return properties != null ? properties : Collections.emptyMap();
    }

    public Optional<Join> getJoin() {
        return Optional.ofNullable(join);
    }

    public Optional<JoinTable> getJoinTable() {
        return Optional.ofNullable(joinTable);
    }

    public List<T> getTypeHierarchy() {
        T superType = self();
        List<T> hierarchy = new ArrayList<>();
        do {
            hierarchy.add(superType);
        } while ((superType = superType.getSuperType().orElse(null)) != null);

        return hierarchy;
    }

    public boolean isSubTypeOf(T type) {
        T superType = self();
        while ((superType = superType.getSuperType().orElse(null)) != null) {
            if (superType == type) {
                return true;
            }
        }

        return false;
    }
}
