/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.operation.exporter.options;

public enum ValidityMode {
    VALID("valid"),
    INVALID("invalid"),
    ALL("all");

    private final String value;

    ValidityMode(String value) {
        this.value = value;
    }

    public String toValue() {
        return value;
    }

    public static ValidityMode fromValue(String value) {
        for (ValidityMode v : ValidityMode.values()) {
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
