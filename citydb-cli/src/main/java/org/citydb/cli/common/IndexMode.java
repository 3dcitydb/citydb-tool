/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.cli.common;

public enum IndexMode {
    KEEP("keep"),
    DROP("drop"),
    DROP_CREATE("dropCreate");

    private final String value;

    IndexMode(String value) {
        this.value = value;
    }

    public String toValue() {
        return value;
    }

    public static IndexMode fromValue(String value) {
        for (IndexMode v : IndexMode.values()) {
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
