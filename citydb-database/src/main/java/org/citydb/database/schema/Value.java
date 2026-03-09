/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.database.schema;

import com.alibaba.fastjson2.JSONObject;
import org.citydb.model.common.Name;

import java.util.Map;
import java.util.Optional;

public class Value {
    private final Column column;
    private final Integer propertyIndex;
    private Property property;

    Value(Column column) {
        this.column = column;
        propertyIndex = null;
    }

    private Value(Integer propertyIndex) {
        this.propertyIndex = propertyIndex;
        column = null;
    }

    static Value of(JSONObject object) throws SchemaException {
        String columnName = object.getString("column");
        String typeName = object.getString("type");
        Integer propertyIndex = object.getInteger("property");

        if (columnName == null && typeName == null && propertyIndex == null) {
            throw new SchemaException("A value must define either a column and type or a property index.");
        } else if (propertyIndex == null) {
            if (columnName == null || typeName == null) {
                throw new SchemaException("A value must define both a column and type.");
            }

            ColumnType type = ColumnType.of(typeName);
            if (type == null) {
                throw new SchemaException("The value uses an unsupported column data type " + typeName + ".");
            }

            return new Value(new Column(columnName, type));
        } else {
            return new Value(propertyIndex);
        }
    }

    public Optional<Column> getColumn() {
        return Optional.ofNullable(column);
    }

    public Optional<Property> getProperty() {
        return Optional.ofNullable(property);
    }

    void postprocess(Map<Name, Property> properties) throws SchemaException {
        if (propertyIndex != null) {
            if (propertyIndex < 0 || propertyIndex >= properties.size()) {
                throw new SchemaException("The property index of the value is out of bounds.");
            }

            property = properties.values().stream()
                    .skip(propertyIndex)
                    .findFirst().orElse(null);
        }
    }
}
