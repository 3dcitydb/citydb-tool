/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.query.lod;

public enum LodFilterMode {
    AND("and"),
    OR("or");

    private final String value;

    LodFilterMode(String value) {
        this.value = value;
    }

    public String toValue() {
        return value;
    }

    public static LodFilterMode fromValue(String value) {
        for (LodFilterMode v : LodFilterMode.values()) {
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
