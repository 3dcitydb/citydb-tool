/*
 * citydb-tool - Command-line tool for the 3D City Database
 * https://www.3dcitydb.org/
 *
 * Copyright 2022-2025
 * virtualcitysystems GmbH, Germany
 * https://vc.systems/
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
