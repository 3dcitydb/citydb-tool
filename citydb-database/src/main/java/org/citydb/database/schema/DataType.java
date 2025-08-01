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
import org.citydb.database.adapter.DatabaseAdapter;
import org.citydb.model.common.Name;
import org.citydb.model.common.Namespaces;

import java.util.Map;
import java.util.Optional;

public class DataType extends Type<DataType> implements ValueObject {
    public static final DataType UNDEFINED = new DataType(1, "core:Undefined", Name.of("Undefined", Namespaces.CORE),
            Table.PROPERTY, null, false, null, null, null, null, null);

    private final Value value;

    private DataType(int id, String identifier, Name name, Table table, String description, boolean isAbstract,
                     Integer superTypeId, Map<Name, Property> properties, Value value, Join join, JoinTable joinTable) {
        super(id, identifier, name, table, description, isAbstract, superTypeId, properties, join, joinTable);
        this.value = value;
    }

    static DataType of(int id, Name name, boolean isAbstract, Integer superTypeId, JSONObject object,
                       DatabaseAdapter adapter) throws SchemaException {
        String identifier = object.getString("identifier");
        String tableName = object.getString("table");
        String description = object.getString("description");
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
            throw new SchemaException("The table " + tableName + " of data type (ID: " + id + ") is not supported.");
        }

        try {
            return new DataType(id, identifier, name, table, description, isAbstract, superTypeId,
                    propertiesArray != null ? Type.buildProperties(propertiesArray, adapter) : null,
                    valueObject != null ? Value.of(valueObject) : null,
                    joinObject != null ? Join.of(joinObject) : null,
                    joinTableObject != null ? JoinTable.of(joinTableObject) : null);
        } catch (SchemaException e) {
            throw new SchemaException("Failed to build data type (ID: " + id + ").", e);
        }
    }

    @Override
    public Optional<Value> getValue() {
        return Optional.ofNullable(value);
    }

    @Override
    void postprocess(SchemaMapping schemaMapping) throws SchemaException {
        super.postprocess(schemaMapping);

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

    @Override
    DataType self() {
        return this;
    }
}
