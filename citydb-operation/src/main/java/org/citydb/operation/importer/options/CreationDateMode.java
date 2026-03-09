/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.operation.importer.options;

public enum CreationDateMode {
    ATTRIBUTE_OR_NOW("attributeOrNow"),
    OVERWRITE_WITH_FIXED("overwriteWithFixed"),
    OVERWRITE_WITH_NOW("overwriteWithNow");

    private final String value;

    CreationDateMode(String value) {
        this.value = value;
    }

    public String toValue() {
        return value;
    }

    public static CreationDateMode fromValue(String value) {
        for (CreationDateMode v : CreationDateMode.values()) {
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
