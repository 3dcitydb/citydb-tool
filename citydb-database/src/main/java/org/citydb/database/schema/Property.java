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

import com.alibaba.fastjson2.JSONObject;
import org.citydb.model.common.Name;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public class Property {
    private final Name name;
    private final Table table;
    private final Integer parentIndex;
    private final String typeIdentifier;
    private final Value value;
    private final JoinTable joinTable;
    private DataType type;
    private Map<Name, Property> properties;
    private Join join;

    Property(Name name, Table table, Integer parentIndex, String typeIdentifier, Value value, Join join,
             JoinTable joinTable) {
        this.name = name;
        this.table = table;
        this.parentIndex = parentIndex;
        this.typeIdentifier = typeIdentifier;
        this.value = value;
        this.join = join;
        this.joinTable = joinTable;
    }

    static Property of(JSONObject object) throws SchemaException {
        String propertyName = object.getString("name");
        String namespace = object.getString("namespace");
        String tableName = object.getString("table");
        Integer parentIndex = object.getInteger("parent");
        JSONObject valueObject = object.getJSONObject("value");
        String typeIdentifier = object.getString("type");
        JSONObject joinObject = object.getJSONObject("join");
        JSONObject joinTableObject = object.getJSONObject("joinTable");

        if (propertyName == null) {
            throw new SchemaException("No name defined for the property.");
        } else if (namespace == null) {
            throw new SchemaException("No namespace defined for the property.");
        } else if (tableName != null && joinObject == null && joinTableObject == null) {
            throw new SchemaException("No join or join table defined for the property table " + tableName + ".");
        } else if (valueObject != null && typeIdentifier != null) {
            throw new SchemaException("A property must not define both a value and a type.");
        } else if (joinObject != null && joinTableObject != null) {
            throw new SchemaException("A property must not define both a join and a join table.");
        }

        Table table = Table.of(tableName);
        if (tableName != null && table == null) {
            throw new SchemaException("The property table " + tableName + " is not supported.");
        }

        return new Property(Name.of(propertyName, namespace), table, parentIndex, typeIdentifier,
                valueObject != null ? Value.of(valueObject) : null,
                joinObject != null ? Join.of(joinObject) : null,
                joinTableObject != null ? JoinTable.of(joinTableObject) : null);
    }

    public Name getName() {
        return name;
    }

    public Optional<Table> getTable() {
        return Optional.ofNullable(table);
    }

    Integer getParentIndex() {
        return parentIndex;
    }

    public Optional<Value> getValue() {
        return Optional.ofNullable(value);
    }

    public Optional<DataType> getType() {
        return Optional.ofNullable(type);
    }

    public Map<Name, Property> getProperties() {
        return properties != null ? properties : Collections.emptyMap();
    }

    void addProperty(Property property) {
        if (properties == null) {
            properties = new LinkedHashMap<>();
        }

        properties.put(property.name, property);
    }

    public Optional<Join> getJoin() {
        return Optional.ofNullable(join);
    }

    Property setJoin(Join join) {
        this.join = join;
        return this;
    }

    public Optional<JoinTable> getJoinTable() {
        return Optional.ofNullable(joinTable);
    }

    void postprocess(Map<Name, Property> properties, SchemaMapping schemaMapping) throws SchemaException {
        if (parentIndex != null) {
            if (parentIndex < 0 || parentIndex >= properties.size()) {
                throw new SchemaException("The parent index of the property is out of bounds.");
            }

            properties.values().stream()
                    .skip(parentIndex)
                    .findFirst().ifPresent(property -> property.addProperty(this));
        }

        if (value != null) {
            value.postprocess(properties);
        } else if (typeIdentifier != null) {
            type = schemaMapping.getDataTypeByIdentifier(typeIdentifier);
            if (type == DataType.UNDEFINED) {
                throw new SchemaException("The property references an undefined type " + typeIdentifier + ".");
            }
        }
    }
}
