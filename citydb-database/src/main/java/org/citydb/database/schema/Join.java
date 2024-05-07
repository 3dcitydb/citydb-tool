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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class Join {
    private final Table table;
    private final String fromColumn;
    private final String toColumn;
    private final List<Condition> conditions;

    Join(Table table, String fromColumn, String toColumn, List<Condition> conditions) {
        this.table = table;
        this.fromColumn = fromColumn;
        this.toColumn = toColumn;
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

        List<Condition> conditions = null;
        if (conditionsArray != null && !conditionsArray.isEmpty()) {
            conditions = new ArrayList<>();
            for (Object item : conditionsArray) {
                if (item instanceof JSONObject conditionObject) {
                    conditions.add(Condition.of(conditionObject));
                }
            }

            if (conditions.size() != conditionsArray.size()) {
                throw new SchemaException("The conditions array contains invalid conditions.");
            }
        }

        return new Join(table, fromColumn.toLowerCase(Locale.ROOT), toColumn.toLowerCase(Locale.ROOT), conditions);
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

    public List<Condition> getConditions() {
        return conditions != null ? conditions : Collections.emptyList();
    }
}
