/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.model.property;

public enum RelationType {
    RELATES(0),
    CONTAINS(1);

    private final int value;

    RelationType(int value) {
        this.value = value;
    }

    public int getDatabaseValue() {
        return value;
    }

    public static RelationType fromDatabaseValue(int value) {
        return value == 1 ? CONTAINS : RELATES;
    }
}
