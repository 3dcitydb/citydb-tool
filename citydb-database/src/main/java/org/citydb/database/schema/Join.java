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

import java.util.Locale;

public class Join {
    private final Table table;
    private final String fromColumn;
    private final String toColumn;

    Join(Table table, String fromColumn, String toColumn) {
        this.table = table;
        this.fromColumn = fromColumn;
        this.toColumn = toColumn;
    }

    static Join of(JSONObject object) throws SchemaException {
        String tableName = object.getString("table");
        String fromColumn = object.getString("fromColumn");
        String toColumn = object.getString("toColumn");

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

        return new Join(table, fromColumn.toLowerCase(Locale.ROOT), toColumn.toLowerCase(Locale.ROOT));
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
}
