/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.util.report.options;

public enum FeatureScope {
    ALL("all"),
    ACTIVE("active");

    private final String value;

    FeatureScope(String value) {
        this.value = value;
    }

    public String toValue() {
        return value;
    }

    public static FeatureScope fromValue(String value) {
        for (FeatureScope v : FeatureScope.values()) {
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
