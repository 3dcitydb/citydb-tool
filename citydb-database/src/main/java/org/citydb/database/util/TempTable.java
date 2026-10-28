/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.database.util;

import org.citydb.database.DatabaseException;
import org.citydb.database.adapter.DatabaseAdapter;
import org.citydb.database.schema.Index;
import org.citydb.sqlbuilder.schema.Table;

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
    private final String schema;
    private final String qualifiedName;
    private final Map<String, DataType> columns;
    private final Map<String, Map<String, Integer>> properties;
    private final TempTableHelper helper;
    private boolean closed;

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

    private TempTable(Connection connection, String name, Map<String, DataType> columns,
                      Map<String, Map<String, Integer>> properties, DatabaseAdapter adapter) {
        this.connection = connection;
        this.name = name;
        this.columns = columns;
        this.properties = properties;
        schema = adapter.getConnectionDetails().getSchema();
        qualifiedName = schema + "." + name;
        helper = adapter.getSchemaAdapter().getTempTableHelper();
    }

    public static Builder builder(DatabaseAdapter adapter) {
        return new Builder(adapter);
    }

    public String getName() {
        return name;
    }

    public String getQualifiedName() {
        return qualifiedName;
    }

    public Table getTable() {
        return Table.of(name, schema);
    }

    public Connection getConnection() {
        return connection;
    }

    public List<String> getColumns() {
        return List.copyOf(columns.keySet());
    }

    public boolean hasColumn(String column) {
        return columns.containsKey(column.toLowerCase(Locale.ROOT));
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
        ensureOpen();
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("select count(*) from " + qualifiedName)) {
            return rs.next() ? rs.getLong(1) : 0;
        }
    }

    public TempTable clear() throws SQLException {
        ensureOpen();
        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate("delete from " + qualifiedName);
            return this;
        }
    }

    private void ensureOpen() throws SQLException {
        if (closed) {
            throw new SQLException("The temporary table " + qualifiedName + " is closed.");
        }
    }

    private int getProperty(String column, String name) {
        return properties.getOrDefault(column.toLowerCase(Locale.ROOT), Map.of()).getOrDefault(name, -1);
    }

    @Override
    public void close() throws SQLException {
        if (!closed) {
            closed = true;
            try (Statement stmt = connection.createStatement()) {
                stmt.executeUpdate(helper.getDropTempTable(name));
            }
        }
    }

    public static class Builder {
        private final DatabaseAdapter adapter;
        private final Map<String, DataType> columns = new LinkedHashMap<>();
        private final Map<List<String>, Boolean> indexes = new LinkedHashMap<>();
        private final Map<String, Map<String, Integer>> properties = new HashMap<>();

        Builder(DatabaseAdapter adapter) {
            this.adapter = adapter;
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
            return index(column, false);
        }

        public Builder index(String column, boolean ignoreNulls) {
            return index(List.of(column), ignoreNulls);
        }

        public Builder index(List<String> columns) {
            return index(columns, false);
        }

        public Builder index(List<String> columns, boolean ignoreNulls) {
            if (columns == null || columns.isEmpty()) {
                throw new IllegalArgumentException("Index columns must not be null or empty.");
            }

            indexes.put(columns.stream()
                    .map(column -> column.toLowerCase(Locale.ROOT))
                    .toList(), ignoreNulls);
            return this;
        }

        private Builder addProperty(String column, String name, int value) {
            properties.computeIfAbsent(column.toLowerCase(Locale.ROOT), v -> new HashMap<>()).put(name, value);
            return this;
        }

        private String createUniqueName() {
            return Long.toUnsignedString(ThreadLocalRandom.current().nextLong() ^ System.currentTimeMillis());
        }

        public TempTable create(Connection connection) throws DatabaseException, SQLException {
            Objects.requireNonNull(connection, "The connection must not be null.");
            if (columns.isEmpty()) {
                throw new DatabaseException("No columns have been defined for the temporary table.");
            }

            for (List<String> indexColumns : indexes.keySet()) {
                for (String indexColumn : indexColumns) {
                    DataType type = columns.get(indexColumn);
                    if (type == null) {
                        throw new DatabaseException("The index column '" + indexColumn + "' has not been defined " +
                                "for the temporary table.");
                    } else if (type == DataType.GEOMETRY) {
                        throw new DatabaseException("Indexes on geometry columns are not supported (column '" +
                                indexColumn + "').");
                    }
                }
            }

            String name = "tmp_" + createUniqueName();
            TempTableHelper helper = adapter.getSchemaAdapter().getTempTableHelper();
            boolean created = false;
            try {
                TempTable tempTable = new TempTable(connection, name, new LinkedHashMap<>(columns), copyProperties(),
                        adapter);
                try (Statement stmt = connection.createStatement()) {
                    stmt.executeUpdate(helper.getCreateTempTable(name,
                            columns.entrySet().stream().collect(Collectors.toMap(
                                    Map.Entry::getKey,
                                    e -> tempTable.getDatabaseType(e.getKey()),
                                    (a, b) -> a,
                                    LinkedHashMap::new))));
                    created = true;
                }

                for (Map.Entry<List<String>, Boolean> entry : indexes.entrySet()) {
                    adapter.getSchemaAdapter().getIndexHelper().create(
                            Index.of("idx_" + createUniqueName(), name, entry.getKey(), Index.Type.NORMAL),
                            entry.getValue(), connection);
                }

                return tempTable;
            } catch (Exception e) {
                if (created) {
                    try (Statement stmt = connection.createStatement()) {
                        stmt.executeUpdate(helper.getDropTempTable(name));
                    } catch (SQLException error) {
                        e.addSuppressed(error);
                    }
                }

                throw e;
            }
        }

        private Map<String, Map<String, Integer>> copyProperties() {
            Map<String, Map<String, Integer>> copy = new HashMap<>();
            properties.forEach((column, values) -> copy.put(column, Map.copyOf(values)));
            return copy;
        }
    }
}
