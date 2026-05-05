/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.database.schema;

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

    Value(Integer propertyIndex) {
        this.propertyIndex = propertyIndex;
        column = null;
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
