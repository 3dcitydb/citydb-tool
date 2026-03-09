/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.operation.deleter.options;

public enum DeleteMode {
    DELETE("delete"),
    TERMINATE("terminate");

    private final String value;

    DeleteMode(String value) {
        this.value = value;
    }

    public String toValue() {
        return value;
    }

    public static DeleteMode fromValue(String value) {
        for (DeleteMode v : DeleteMode.values()) {
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