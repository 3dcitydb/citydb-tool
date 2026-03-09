/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.util.changelog.options;

public enum SortOrder {
    LATEST_FIRST("latestFirst"),
    OLDEST_FIRST("oldestFirst"),
    ;

    private final String value;

    SortOrder(String value) {
        this.value = value;
    }

    public String toValue() {
        return value;
    }

    public static SortOrder fromValue(String value) {
        for (SortOrder v : SortOrder.values()) {
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
