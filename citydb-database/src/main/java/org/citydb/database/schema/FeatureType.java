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

public class FeatureType extends Type<FeatureType> {
    public static final FeatureType UNDEFINED = new FeatureType(1, "core:Undefined",
            Name.of("Undefined", Namespaces.CORE), Table.FEATURE, null, false, false, null, null, null, null);

    private final boolean isTopLevel;

    private FeatureType(int id, String identifier, Name name, Table table, String description, boolean isAbstract,
                        boolean isTopLevel, Integer superTypeId, Map<Name, Property> properties, Join join,
                        JoinTable joinTable) {
        super(id, identifier, name, table, description, isAbstract, superTypeId, properties, join, joinTable);
        this.isTopLevel = isTopLevel;
    }

    static FeatureType of(int id, Name name, boolean isAbstract, boolean isTopLevel, Integer superTypeId,
                          JSONObject object, DatabaseAdapter adapter) throws SchemaException {
        String identifier = object.getString("identifier");
        String tableName = object.getString("table");
        String description = object.getString("description");
        JSONArray propertiesArray = object.getJSONArray("properties");
        JSONObject joinObject = object.getJSONObject("join");
        JSONObject joinTableObject = object.getJSONObject("joinTable");

        if (identifier == null) {
            throw new SchemaException("No identifier defined for feature type (ID " + id + ").");
        } else if (tableName == null) {
            throw new SchemaException("No table defined for feature type (ID " + id + ").");
        } else if (joinObject != null && joinTableObject != null) {
            throw new SchemaException("The feature type (ID " + id + ") defines both a join and a join table.");
        }

        Table table = Table.of(tableName);
        if (table == null) {
            throw new SchemaException("The table " + tableName + " of feature type (ID: " + id + ") is not supported.");
        }

        try {
            return new FeatureType(id, identifier, name, table, description, isAbstract, isTopLevel, superTypeId,
                    propertiesArray != null ? Type.buildProperties(propertiesArray, adapter) : null,
                    joinObject != null ? Join.of(joinObject) : null,
                    joinTableObject != null ? JoinTable.of(joinTableObject) : null);
        } catch (SchemaException e) {
            throw new SchemaException("Failed to build feature type (ID: " + id + ").", e);
        }
    }

    public boolean isTopLevel() {
        return isTopLevel;
    }

    @Override
    void postprocess(SchemaMapping schemaMapping) throws SchemaException {
        super.postprocess(schemaMapping);

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
                            property.setJoin(new Join(Table.PROPERTY, "id", "feature_id")
                                    .postprocess(property, schemaMapping));
                        }
                    }

                    Join join = property.getJoin().orElse(null);
                    if (join != null
                            && join.getTable() == Table.PROPERTY
                            && !join.hasConditionOn("parent_id")
                            && hasDuplicateChildProperty(property)) {
                        join.addCondition(new Condition(new Column("parent_id", SimpleType.INTEGER), "null"));
                    }
                }

                properties.values().removeIf(property -> property.getParentIndex() != null);
            }
        } catch (SchemaException e) {
            throw new SchemaException("Failed to build feature type (ID " + id + ").", e);
        }
    }

    private boolean hasDuplicateChildProperty(Property property) {
        Property duplicate = property.getProperties().get(property.getName());
        if (duplicate == null) {
            duplicate = property.getType()
                    .map(type -> type.getProperties().get(property.getName()))
                    .orElse(null);
        }

        return duplicate != null && duplicate.getJoin().isPresent();
    }

    @Override
    FeatureType self() {
        return this;
    }
}
