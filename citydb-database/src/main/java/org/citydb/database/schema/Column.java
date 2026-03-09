/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.database.schema;

import java.util.Locale;

public class Column {
    private final String name;
    private final ColumnType type;

    Column(String name, ColumnType type) {
        this.name = name.toLowerCase(Locale.ROOT);
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public ColumnType getType() {
        return type;
    }
}
