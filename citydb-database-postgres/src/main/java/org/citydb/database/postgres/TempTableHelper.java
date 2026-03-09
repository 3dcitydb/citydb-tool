/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.database.postgres;

import org.citydb.database.adapter.DatabaseAdapter;

import java.util.Map;

public class TempTableHelper implements org.citydb.database.util.TempTableHelper {
    private final DatabaseAdapter adapter;

    TempTableHelper(DatabaseAdapter adapter) {
        this.adapter = adapter;
    }

    @Override
    public String getInteger() {
        return "integer";
    }

    @Override
    public String getLong() {
        return "bigint";
    }

    @Override
    public String getDouble() {
        return "double precision";
    }

    @Override
    public String getNumeric() {
        return "numeric";
    }

    @Override
    public String getNumeric(int precision) {
        return getNumeric() + "(" + precision + ")";
    }

    @Override
    public String getNumeric(int precision, int scale) {
        return getNumeric() + "(" + precision + "," + scale + ")";
    }

    @Override
    public String getString() {
        return "text";
    }

    @Override
    public String getString(int size) {
        return "varchar(" + size + ")";
    }

    @Override
    public String getTimeStamp() {
        return "timestamp";
    }

    @Override
    public String getTimeStampWithTimeZone() {
        return "timestamp with time zone";
    }

    @Override
    public String getGeometry() {
        return "geometry(geometryz)";
    }

    @Override
    public String getCreateTempTable(String name, Map<String, String> columns) {
        return "create unlogged table " + adapter.getConnectionDetails().getSchema() + "." + name + " (" +
                columns.entrySet().stream()
                        .map(e -> e.getKey() + " " + e.getValue())
                        .reduce((a, b) -> a + ", " + b)
                        .orElse("") +
                ")";
    }
}
