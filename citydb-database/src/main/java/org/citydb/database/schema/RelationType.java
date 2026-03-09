/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.database.schema;

import java.util.Locale;

public enum RelationType {
    RELATES("relates"), CONTAINS("contains");

    private final String name;

    RelationType(String name) {
        this.name = name;
    }

    public static RelationType of(String name) {
        return name != null ? switch (name.toLowerCase(Locale.ROOT)) {
            case "relates" -> RelationType.RELATES;
            case "contains" -> RelationType.CONTAINS;
            default -> null;
        } : null;
    }

    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return name;
    }
}
