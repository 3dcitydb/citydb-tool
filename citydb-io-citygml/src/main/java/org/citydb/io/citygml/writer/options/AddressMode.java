/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.io.citygml.writer.options;

public enum AddressMode {
    COLUMNS_FIRST("columnsFirst"),
    COLUMNS_ONLY("columnsOnly"),
    XAL_SOURCE_FIRST("xalSourceFirst"),
    XAL_SOURCE_ONLY("xalSourceOnly");

    private final String value;

    AddressMode(String value) {
        this.value = value;
    }

    public String toValue() {
        return value;
    }

    public static AddressMode fromValue(String value) {
        for (AddressMode v : AddressMode.values()) {
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
