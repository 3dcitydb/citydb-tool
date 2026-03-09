/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.util.changelog.options;

public enum BboxMode {
    INTERSECTS("intersects"),
    CONTAINS("contains");

    private final String value;

    BboxMode(String value) {
        this.value = value;
    }

    public String toValue() {
        return value;
    }

    public static BboxMode fromValue(String value) {
        for (BboxMode v : BboxMode.values()) {
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
