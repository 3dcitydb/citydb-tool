/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.io.citygml.reader.options;

public enum LodMappingStrategy {
    MAXIMUM("maximum"),
    MINIMUM("minimum");

    private final String value;

    LodMappingStrategy(String value) {
        this.value = value;
    }

    public String toValue() {
        return value;
    }

    public static LodMappingStrategy fromValue(String value) {
        for (LodMappingStrategy v : LodMappingStrategy.values()) {
            if (v.value.equals(value)) {
                return v;
            }
        }

        return null;
    }

    @Override
    public String toString() {
        return value;
    }
}
