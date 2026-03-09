/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.database.schema;

public interface ColumnType {
    String getIdentifier();

    static ColumnType of(String name) {
        ColumnType type = SimpleType.of(name);
        return type != null ? type : GeometryType.of(name);
    }
}
