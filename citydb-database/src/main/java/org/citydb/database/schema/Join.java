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

import java.util.*;

public class Join {
    private final Table table;
    private final String fromColumn;
    private final String toColumn;
    private Map<String, Condition> conditions;

    private Join(Table table, String fromColumn, String toColumn, Map<String, Condition> conditions) {
        this.table = table;
        this.fromColumn = fromColumn.toLowerCase(Locale.ROOT);
        this.toColumn = toColumn.toLowerCase(Locale.ROOT);
        this.conditions = conditions;
    }

    Join(Table table, String fromColumn, String toColumn) {
        this(table, fromColumn, toColumn, null);
    }

    static Join of(JSONObject object) throws SchemaException {
        String tableName = object.getString("table");
        String fromColumn = object.getString("fromColumn");
        String toColumn = object.getString("toColumn");
        JSONArray conditionsArray = object.getJSONArray("conditions");

        if (tableName == null) {
            throw new SchemaException("No table defined for the join.");
        } else if (fromColumn == null) {
            throw new SchemaException("No from-column defined for the join.");
        } else if (toColumn == null) {
            throw new SchemaException("No to-column defined for the join.");
        }

        Table table = Table.of(tableName);
        if (table == null) {
            throw new SchemaException("The join target " + tableName + " is not supported.");
        }

        Map<String, Condition> conditions = null;
        if (conditionsArray != null && !conditionsArray.isEmpty()) {
            conditions = new LinkedHashMap<>();
            for (Object item : conditionsArray) {
                if (item instanceof JSONObject conditionObject) {
                    Condition condition = Condition.of(conditionObject);
                    conditions.put(condition.getColumn().getName(), condition);
                }
            }

            if (conditions.size() != conditionsArray.size()) {
                throw new SchemaException("The conditions array contains invalid conditions.");
            }
        }

        return new Join(table, fromColumn, toColumn, conditions);
    }

    public Table getTable() {
        return table;
    }

    public String getFromColumn() {
        return fromColumn;
    }

    public String getToColumn() {
        return toColumn;
    }

    public Collection<Condition> getConditions() {
        return conditions != null ? conditions.values() : Collections.emptyList();
    }

    boolean hasConditionOn(String column) {
        return conditions != null && conditions.containsKey(column);
    }

    void addCondition(Condition condition) {
        if (conditions == null) {
            conditions = new LinkedHashMap<>();
        }

        conditions.put(condition.getColumn().getName(), condition);
    }

    Join postprocess(Joinable joinable, SchemaMapping schemaMapping) {
        if (table == Table.PROPERTY
                && !hasConditionOn("name")
                && !hasConditionOn("namespace_id")) {
            addCondition(new Condition(new Column("name", SimpleType.STRING),
                    joinable.getName().getLocalName()));
            addCondition(new Condition(new Column("namespace_id", SimpleType.INTEGER),
                    String.valueOf(schemaMapping.getNamespace(joinable.getName()).getId())));
        }

        return this;
    }
}
