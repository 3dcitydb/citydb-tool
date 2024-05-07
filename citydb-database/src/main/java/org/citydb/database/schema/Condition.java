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

public class Condition {
    private final Column column;
    private final String value;

    private Condition(Column column, String value) {
        this.column = column;
        this.value = value;
    }

    static Condition of(JSONObject object) throws SchemaException {
        String columnName = object.getString("column");
        String value = object.getString("value");
        String typeName = object.getString("type");

        if (columnName == null) {
            throw new SchemaException("No column defined for the join condition.");
        } else if (value == null) {
            throw new SchemaException("No value defined for the join condition.");
        } else if (typeName == null) {
            throw new SchemaException("No column data type defined for the join condition.");
        }

        ColumnType type = ColumnType.of(typeName);
        if (type == null) {
            throw new SchemaException("The join condition uses an unsupported column data type " + typeName + ".");
        }

        return new Condition(new Column(columnName, type), value);
    }

    public Column getColumn() {
        return column;
    }

    public String getValue() {
        return value;
    }
}
