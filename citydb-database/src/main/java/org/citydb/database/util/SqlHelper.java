/*
 * citydb-tool - Command-line tool for the 3D City Database
 * https://www.3dcitydb.org/
 *
 * Copyright 2022-2024
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

import org.citydb.database.adapter.DatabaseAdapter;
import org.citydb.database.geometry.GeometryException;
import org.citydb.model.geometry.Envelope;
import org.citydb.model.geometry.Geometry;
import org.citydb.sqlbuilder.SqlBuildOptions;
import org.citydb.sqlbuilder.SqlBuilder;
import org.citydb.sqlbuilder.common.SqlObject;
import org.citydb.sqlbuilder.function.Function;
import org.citydb.sqlbuilder.literal.Placeholder;
import org.citydb.sqlbuilder.query.Select;
import org.citydb.sqlbuilder.schema.Table;
import org.citydb.sqlbuilder.schema.WildcardColumn;

import java.sql.*;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class SqlHelper {
    private final DatabaseAdapter adapter;

    private SqlHelper(DatabaseAdapter adapter) {
        this.adapter = adapter;
    }

    public static SqlHelper newInstance(DatabaseAdapter adapter) {
        return new SqlHelper(adapter);
    }

    public PreparedStatement prepareStatement(SqlObject statement, Connection connection) throws SQLException {
        PreparedStatement stmt = connection.prepareStatement(statement.toSql());
        List<Placeholder> placeholders = statement.getPlaceholders();
        for (int i = 0; i < placeholders.size(); i++) {
            int index = i + 1;
            Object value = placeholders.get(i).getValue()
                    .orElseThrow(() -> new SQLException("The placeholder at index " + index + " lacks a value."));

            if (value instanceof Boolean booleanValue) {
                stmt.setInt(index, booleanValue ? 1 : 0);
            } else if (value instanceof String stringValue) {
                stmt.setString(index, stringValue);
            } else if (value instanceof Double doubleValue) {
                stmt.setDouble(index, doubleValue);
            } else if (value instanceof Long longValue) {
                stmt.setLong(index, longValue);
            } else if (value instanceof Integer intValue) {
                stmt.setInt(index, intValue);
            } else if (value instanceof Number number) {
                stmt.setDouble(index, number.doubleValue());
            } else if (value instanceof Date date) {
                stmt.setDate(index, date);
            } else if (value instanceof Timestamp timestamp) {
                stmt.setTimestamp(index, timestamp);
            } else if (value instanceof Geometry<?> geometry) {
                prepareStatement(index, geometry, stmt);
            } else if (value instanceof Envelope envelope) {
                prepareStatement(index, envelope.convertToPolygon(), stmt);
            } else {
                throw new SQLException("A placeholder value of type " + value.getClass().getName() +
                        " is not supported.");
            }
        }

        return stmt;
    }

    private void prepareStatement(int index, Geometry<?> geometry, PreparedStatement stmt) throws SQLException {
        try {
            stmt.setObject(index, adapter.getGeometryAdapter().getGeometry(geometry),
                    adapter.getGeometryAdapter().getGeometrySqlType());
        } catch (Exception e) {
            throw new SQLException("Failed to convert geometry to database representation.", e);
        }
    }

    public long count(Select select) throws SQLException {
        try (Connection connection = adapter.getPool().getConnection()) {
            return count(select, connection);
        }
    }

    public long count(Select select, Connection connection) throws SQLException {
        Table table = Table.of(Select.of(select).removeOrderBy());
        Select count = Select.newInstance()
                .select(Function.of("count", WildcardColumn.of(table)))
                .from(table);

        try (PreparedStatement stmt = prepareStatement(count, connection);
             ResultSet rs = stmt.executeQuery()) {
            return rs.next() ? rs.getLong(1) : 0;
        }
    }

    public String toSql(SqlObject object, SqlBuildOptions options) {
        options.setPlaceholderBuilder((placeholder, op) -> {
            Object value = placeholder.getValue().orElse(null);
            if (value instanceof Boolean booleanValue) {
                return booleanValue ? "1" : "0";
            } else if (value instanceof String
                    || value instanceof Date) {
                return "'" + value + "'";
            } else if (value instanceof Timestamp timestamp) {
                return "'" + timestamp.toInstant()
                        .atOffset(ZoneOffset.UTC)
                        .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME) + "'";
            } else if (value instanceof Geometry<?> geometry) {
                return toSql(geometry);
            } else if (value instanceof Envelope envelope) {
                return toSql(envelope.convertToPolygon());
            } else {
                return String.valueOf(value);
            }
        });

        return SqlBuilder.newInstance().build(object, options);
    }

    private String toSql(Geometry<?> geometry) {
        try {
            return "'" + adapter.getGeometryAdapter().getAsText(geometry) + "'";
        } catch (GeometryException e) {
            return null;
        }
    }
}
