/*
 * citydb-tool - Command-line tool for the 3D City Database
 * https://www.3dcitydb.org/
 *
 * Copyright © 2025, Oracle and/or its affiliates.
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

package org.citydb.database.oracle;

import org.citydb.database.adapter.DatabaseAdapter;

import java.util.Map;

public class TempTableHelper implements org.citydb.database.util.TempTableHelper {
    private final DatabaseAdapter adapter;

    TempTableHelper(DatabaseAdapter adapter) {
        this.adapter = adapter;
    }

    @Override
    public String getInteger() {
        return "number(10)";
    }

    @Override
    public String getLong() {
        return "number(19)";
    }

    @Override
    public String getDouble() {
        return "binary_double";
    }

    @Override
    public String getNumeric() {
        return "number";
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
        return "varchar2(4000)";
    }

    @Override
    public String getString(int size) {
        assert (size > 0);
        return "varchar2(" + size + ")";
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
        return "mdsys.sdo_geometry";
    }

    @Override
    public String getCreateTempTable(String name, Map<String, String> columns) {
        return "create global temporary table " +
                SchemaAdapter.enquoteSqlName(adapter.getConnectionDetails().getSchema()) + "." +
                SchemaAdapter.enquoteSqlName(name) + " (" +
                columns.entrySet().stream()
                        .map(e -> SchemaAdapter.enquoteSqlName(e.getKey()) + " " + SchemaAdapter.checkSqlType(e.getValue()))
                        .reduce((a, b) -> a + ", " + b)
                        .orElse("") +
                ") on commit preserve rows";
    }
}