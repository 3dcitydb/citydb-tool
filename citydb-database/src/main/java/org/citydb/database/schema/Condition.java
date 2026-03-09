/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.database.schema;

import com.alibaba.fastjson2.JSONObject;

public class Condition {
    private final Column column;
    private final String value;

    Condition(Column column, String value) {
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

        SimpleType type = SimpleType.of(typeName);
        if (!SimpleType.JOIN_CONDITION_TYPES.contains(type)) {
            throw new SchemaException("The join condition uses an unsupported data type " + typeName + ".");
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
