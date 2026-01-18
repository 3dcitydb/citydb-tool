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

package org.citydb.database.util;

import org.citydb.database.DatabaseException;
import org.citydb.database.adapter.DatabaseAdapter;
import org.citydb.database.schema.Index;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

public class TempTable implements AutoCloseable {
    private final Connection connection;
    private final String name;
    private final String prefixedName;
    private final Map<String, DataType> columns;
    private final Map<String, Map<String, Integer>> properties;
    private final TempTableHelper helper;

    private TempTable(Connection connection, String name, Map<String, DataType> columns,
                      Map<String, Map<String, Integer>> properties, DatabaseAdapter adapter) {
        this.connection = connection;
        this.name = name;
        this.columns = columns;
        this.properties = properties;
        prefixedName = adapter.getConnectionDetails().getSchema() + "." + name;
        helper = adapter.getSchemaAdapter().getTempTableHelper();
    }

    public static Builder builder(DatabaseAdapter adapter) {
        return new Builder(adapter);
    }

    public Connection getConnection() {
        return connection;
    }

    public String getName() {
        return name;
    }

    public List<String> getColumns() {
        return List.copyOf(columns.keySet());
    }

    public DataType getDataType(String column) {
        return columns.get(column.toLowerCase(Locale.ROOT));
    }

    public String getDatabaseType(String column) {
        DataType type = getDataType(column);
        if (type != null) {
            return switch (type) {
                case INTEGER -> helper.getInteger();
                case LONG -> helper.getLong();
                case DOUBLE -> helper.getDouble();
                case NUMERIC -> {
                    int precision = getProperty(column, "precision");
                    int scale = getProperty(column, "scale");
                    yield precision > 0 ? helper.getNumeric(precision, scale) : helper.getNumeric();
                }
                case STRING -> {
                    int size = getProperty(column, "size");
                    yield size > 0 ? helper.getString(size) : helper.getString();
                }
                case TIMESTAMP -> helper.getTimeStamp();
                case TIMESTAMP_WITH_TIMEZONE -> helper.getTimeStampWithTimeZone();
                case GEOMETRY -> helper.getGeometry();
            };
        } else {
            return null;
        }
    }

    public boolean isEmpty() throws SQLException {
        return size() == 0;
    }

    public long size() throws SQLException {
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("select count(*) from " + prefixedName)) {
            return rs.next() ? rs.getLong(1) : 0;
        }
    }

    public TempTable truncate() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate("delete from " + prefixedName);
            return this;
        }
    }

    private int getProperty(String column, String name) {
        return properties.computeIfAbsent(column.toLowerCase(Locale.ROOT), v -> new HashMap<>())
                .getOrDefault(name, -1);
    }

    @Override
    public void close() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate("drop table " + prefixedName);
            connection.commit();
        } finally {
            connection.close();
        }
    }

    public enum DataType {
        INTEGER,
        LONG,
        DOUBLE,
        NUMERIC,
        STRING,
        TIMESTAMP,
        TIMESTAMP_WITH_TIMEZONE,
        GEOMETRY
    }

    public static class Builder {
        private final DatabaseAdapter adapter;
        private final String name;
        private final Map<String, DataType> columns = new LinkedHashMap<>();
        private Map<String, Boolean> indexes = new HashMap<>();
        private Map<String, Map<String, Integer>> properties = new HashMap<>();

        Builder(DatabaseAdapter adapter) {
            this.adapter = adapter;
            name = "tmp_" + createUniqueName();
        }

        public Builder column(String name, DataType type) {
            columns.put(name.toLowerCase(Locale.ROOT), type);
            return this;
        }

        public Builder intColumn(String name) {
            return column(name, DataType.INTEGER);
        }

        public Builder longColumn(String name) {
            return column(name, DataType.LONG);
        }

        public Builder doubleColumn(String name) {
            return column(name, DataType.DOUBLE);
        }

        public Builder numericColumn(String name) {
            return column(name, DataType.NUMERIC);
        }

        public Builder numericColumn(String name, int precision) {
            return numericColumn(name, precision, 0);
        }

        public Builder numericColumn(String name, int precision, int scale) {
            return numericColumn(name)
                    .addProperty(name, "precision", precision)
                    .addProperty(name, "scale", scale);
        }

        public Builder stringColumn(String name) {
            return column(name, DataType.STRING);
        }

        public Builder stringColumn(String name, int size) {
            return stringColumn(name)
                    .addProperty(name, "size", size);
        }

        public Builder timestampColumn(String name) {
            return timestampColumn(name, false);
        }

        public Builder timestampColumn(String name, boolean withTimezone) {
            return column(name, withTimezone ? DataType.TIMESTAMP_WITH_TIMEZONE : DataType.TIMESTAMP);
        }

        public Builder geometryColumn(String name) {
            return column(name, DataType.GEOMETRY);
        }

        public Builder index(String column) {
            return index(name, false);
        }

        public Builder index(String column, boolean ignoreNulls) {
            indexes.put(column.toLowerCase(Locale.ROOT), ignoreNulls);
            return this;
        }

        private Builder addProperty(String column, String name, int value) {
            properties.computeIfAbsent(column.toLowerCase(Locale.ROOT), v -> new HashMap<>()).put(name, value);
            return this;
        }

        private String createUniqueName() {
            return Long.toUnsignedString(ThreadLocalRandom.current().nextLong() ^ System.currentTimeMillis());
        }

        public TempTable create() throws DatabaseException, SQLException {
            return create(adapter.getPool().getConnection());
        }

        public TempTable create(Connection connection) throws DatabaseException, SQLException {
            if (columns.isEmpty()) {
                throw new DatabaseException("No columns have been defined for the temporary table.");
            }

            TempTable tempTable = new TempTable(connection, name, columns, properties, adapter);
            try (Statement stmt = connection.createStatement()) {
                stmt.executeUpdate(adapter.getSchemaAdapter().getTempTableHelper().getCreateTempTable(name,
                        columns.entrySet().stream().collect(Collectors.toMap(
                                Map.Entry::getKey,
                                e -> tempTable.getDatabaseType(e.getKey()),
                                (a, b) -> a,
                                LinkedHashMap::new))));
            }

            for (Map.Entry<String, Boolean> entry : indexes.entrySet()) {
                adapter.getSchemaAdapter().getIndexHelper().create(
                        Index.of("idx_" + createUniqueName(), name, entry.getKey(),
                                columns.get(entry.getKey()) == DataType.GEOMETRY ?
                                        Index.Type.SPATIAL :
                                        Index.Type.NORMAL),
                        entry.getValue(), connection);
            }

            return tempTable;
        }
    }
}
