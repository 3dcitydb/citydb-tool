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

public class FeatureType {
    public static final FeatureType UNDEFINED = new FeatureType(1, Name.of("Undefined", Namespaces.CORE),
            Table.FEATURE, false, null, null, null, null);

    private final int id;
    private final Name name;
    private final Table table;
    private final boolean topLevel;
    private final Integer superTypeId;
    private final Map<Name, Property> properties;
    private final Join join;
    private final JoinTable joinTable;
    private FeatureType superType;

    FeatureType(int id, Name name, Table table, boolean topLevel, Integer superTypeId,
                Map<Name, Property> properties, Join join, JoinTable joinTable) {
        this.id = id;
        this.name = name;
        this.table = table;
        this.topLevel = topLevel;
        this.superTypeId = superTypeId;
        this.properties = properties;
        this.join = join;
        this.joinTable = joinTable;
    }

    static FeatureType of(int id, Name name, boolean topLevel, Integer superTypeId, JSONObject object) throws SchemaException {
        String tableName = object.getString("table");
        JSONArray propertiesArray = object.getJSONArray("properties");
        JSONObject joinObject = object.getJSONObject("join");
        JSONObject joinTableObject = object.getJSONObject("joinTable");

        if (tableName == null) {
            throw new SchemaException("No table defined for feature type (ID " + id + ").");
        } else if (joinObject != null && joinTableObject != null) {
            throw new SchemaException("The feature type (ID " + id + ") defines both a join and a join table.");
        }

        Table table = Table.of(tableName);
        if (table == null) {
            throw new SchemaException("The feature type table " + tableName + " is not supported.");
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

            return new FeatureType(id, name, table, topLevel, superTypeId, properties,
                    joinObject != null ? Join.of(joinObject) : null,
                    joinTableObject != null ? JoinTable.of(joinTableObject) : null);
        } catch (SchemaException e) {
            throw new SchemaException("Failed to build feature type (ID: " + id + ").", e);
        }
    }

    public int getId() {
        return id;
    }

    public Name getName() {
        return name;
    }

    public Table getTable() {
        return table;
    }

    public boolean isTopLevel() {
        return topLevel;
    }

    public Optional<FeatureType> getSuperType() {
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

    void postprocess(SchemaMapping schemaMapping) throws SchemaException {
        if (superTypeId != null) {
            superType = schemaMapping.getFeatureType(superTypeId);
            if (superType == FeatureType.UNDEFINED) {
                throw new SchemaException("The feature type (ID " + id + ") references an undefined " +
                        "supertype (ID " + superTypeId + ").");
            }
        }

        try {
            if (properties != null) {
                for (Property property : properties.values()) {
                    property.postprocess(properties, schemaMapping);
                    if (property.getJoin().isEmpty() && property.getJoinTable().isEmpty()) {
                        Table table = property.getType().map(DataType::getTable).orElse(null);
                        if (table == Table.PROPERTY) {
                            property.setJoin(new Join(Table.PROPERTY, "id", "feature_id"));
                        }
                    }
                }

                properties.values().removeIf(property -> property.getParentIndex() != null);
            }
        } catch (SchemaException e) {
            throw new SchemaException("Failed to build feature type (ID " + id + ").", e);
        }
    }
}
