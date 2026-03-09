/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.operation.exporter.options;

public enum LodMode {
    KEEP("keep"),
    REMOVE("remove"),
    MINIMUM("minimum"),
    MAXIMUM("maximum");

    private final String value;

    LodMode(String value) {
        this.value = value;
    }

    public String toValue() {
        return value;
    }

    public static LodMode fromValue(String value) {
        for (LodMode v : LodMode.values()) {
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
