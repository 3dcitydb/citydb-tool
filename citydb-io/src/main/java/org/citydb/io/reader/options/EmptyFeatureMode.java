/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.io.reader.options;

public enum EmptyFeatureMode {
    REMOVE("remove"),
    KEEP_TOP_LEVEL("keepTopLevel"),
    KEEP("keep");

    private final String value;

    EmptyFeatureMode(String value) {
        this.value = value;
    }

    public String toValue() {
        return value;
    }

    public static EmptyFeatureMode fromValue(String value) {
        for (EmptyFeatureMode v : EmptyFeatureMode.values()) {
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
