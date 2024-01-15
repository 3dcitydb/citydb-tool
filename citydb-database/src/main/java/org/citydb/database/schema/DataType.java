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
import org.citydb.model.common.Namespaces;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public class DataType {
    public static final DataType UNDEFINED = new DataType(1, "core:Undefined", Name.of("Undefined", Namespaces.CORE),
            Table.PROPERTY, null, null, null, null, null);

    private final int id;
    private final String identifier;
    private final Name name;
    private final Table table;
    private final Integer superTypeId;
    private final Map<Name, Property> properties;
    private final Value value;
    private final Join join;
    private final JoinTable joinTable;
    private DataType superType;

    DataType(int id, String identifier, Name name, Table table, Integer superTypeId, Map<Name, Property> properties,
             Value value, Join join, JoinTable joinTable) {
        this.id = id;
        this.identifier = identifier;
        this.name = name;
        this.table = table;
        this.superTypeId = superTypeId;
        this.properties = properties;
        this.value = value;
        this.join = join;
        this.joinTable = joinTable;
    }

    static DataType of(int id, Name name, Integer superTypeId, JSONObject object) throws SchemaException {
        String identifier = object.getString("identifier");
        String tableName = object.getString("table");
        JSONArray propertiesArray = object.getJSONArray("properties");
        JSONObject valueObject = object.getJSONObject("value");
        JSONObject joinObject = object.getJSONObject("join");
        JSONObject joinTableObject = object.getJSONObject("joinTable");

        if (identifier == null) {
            throw new SchemaException("No identifier defined for data type (ID " + id + ").");
        } else if (tableName == null) {
            throw new SchemaException("No table defined for data type (ID " + id + ").");
        } else if (joinObject != null && joinTableObject != null) {
            throw new SchemaException("The data type (ID " + id + ") defines both a join and a join table.");
        }

        Table table = Table.of(tableName);
        if (table == null) {
            throw new SchemaException("The data type table " + tableName + " is not supported.");
        }

        try {
            Map<Name, Property> properties = null;
            if (propertiesArray != null && !propertiesArray.isEmpty()) {
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

            return new DataType(id, identifier, name, table, superTypeId, properties,
                    valueObject != null ? Value.of(valueObject) : null,
                    joinObject != null ? Join.of(joinObject) : null,
                    joinTableObject != null ? JoinTable.of(joinTableObject) : null);
        } catch (SchemaException e) {
            throw new SchemaException("Failed to build data type (ID: " + id + ").", e);
        }
    }

    public int getId() {
        return id;
    }

    String getIdentifier() {
        return identifier;
    }

    public Name getName() {
        return name;
    }

    public Table getTable() {
        return table;
    }

    public Optional<DataType> getSuperType() {
        return Optional.ofNullable(superType);
    }

    public Optional<Value> getValue() {
        return Optional.ofNullable(value);
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

    void postprocess(SchemaMapping schemaMapping) throws SchemaException {
        if (superTypeId != null) {
            superType = schemaMapping.getDataType(superTypeId);
            if (superType == DataType.UNDEFINED) {
                throw new SchemaException("The data type (ID " + id + ") references an undefined " +
                        "supertype (ID " + superTypeId + ").");
            }
        }

        try {
            if (properties != null) {
                if (value != null) {
                    value.postprocess(properties);
                }

                for (Property property : properties.values()) {
                    property.postprocess(properties, schemaMapping);
                }

                properties.values().removeIf(property -> property.getParentIndex() != null);
            }
        } catch (SchemaException e) {
            throw new SchemaException("Failed to build data type (ID " + id + ").", e);
        }
    }
}
