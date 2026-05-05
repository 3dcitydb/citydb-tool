/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.database.schema;

public class Condition {
    private final Column column;
    private final String value;

    Condition(Column column, String value) {
        this.column = column;
        this.value = value;
    }

    public Column getColumn() {
        return column;
    }

    public String getValue() {
        return value;
    }
}
