/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.cli.importer.options;

public enum ImportMode {
    IMPORT_ALL("importAll"),
    DELETE_EXISTING("deleteExisting"),
    TERMINATE_EXISTING("terminateExisting"),
    SKIP_EXISTING("skipExisting");

    private final String value;

    ImportMode(String value) {
        this.value = value;
    }

    public String toValue() {
        return value;
    }

    public static ImportMode fromValue(String value) {
        for (ImportMode v : ImportMode.values()) {
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
