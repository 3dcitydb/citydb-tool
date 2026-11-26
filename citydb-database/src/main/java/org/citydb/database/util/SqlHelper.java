/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.database.util;

import org.citydb.database.adapter.DatabaseAdapter;
import org.citydb.database.geometry.GeometryException;
import org.citydb.model.geometry.SpatialObject;
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
import java.util.Collection;
import java.util.List;
import java.util.Objects;

public abstract class SqlHelper {
    private final DatabaseAdapter adapter;

    protected SqlHelper(DatabaseAdapter adapter) {
        this.adapter = adapter;
    }

    public abstract void setBytesOrNull(PreparedStatement stmt, int index, byte[] bytes) throws SQLException;

    public abstract void setJsonOrNull(PreparedStatement stmt, int index, String json) throws SQLException;

    public abstract void setGeometryOrNull(PreparedStatement stmt, int index, Object geometry) throws SQLException;

    public abstract void setLongArrayOrNull(PreparedStatement stmt, int index, Collection<Long> values) throws SQLException;

    public abstract void setStringArrayOrNull(PreparedStatement stmt, int index, Collection<String> values) throws SQLException;

    public PreparedStatement prepareStatement(SqlObject statement, Connection connection) throws SQLException {
        return prepareStatement(statement, connection, StatementOptions.defaults());
    }

    public PreparedStatement prepareStatement(SqlObject statement, Connection connection, StatementOptions options) throws SQLException {
        Objects.requireNonNull(options, "The statement options must not be null.");
        PreparedStatement stmt = connection.prepareStatement(statement.toSql());
        try {
            List<Placeholder> placeholders = statement.getPlaceholders();
            for (int i = 0; i < placeholders.size(); i++) {
                int index = i + 1;
                Object value = placeholders.get(i).getValue()
                        .orElseThrow(() -> new SQLException("The placeholder at index " + index + " lacks a value."));

                switch (value) {
                    case Boolean booleanValue -> stmt.setInt(index, booleanValue ? 1 : 0);
                    case String stringValue -> stmt.setString(index, stringValue);
                    case Double doubleValue -> stmt.setDouble(index, doubleValue);
                    case Long longValue -> stmt.setLong(index, longValue);
                    case Integer intValue -> stmt.setInt(index, intValue);
                    case Number number -> stmt.setDouble(index, number.doubleValue());
                    case Date date -> stmt.setDate(index, date);
                    case Timestamp timestamp -> stmt.setTimestamp(index, timestamp);
                    case SpatialObject object -> prepareStatement(index, object, stmt);
                    default -> throw new SQLException("A placeholder value of type " + value.getClass().getName() +
                            " is not supported.");
                }
            }

            if (options.getFetchSize().isPresent()) {
                stmt.setFetchSize(options.getFetchSize().get());
            }

            if (options.getQueryTimeout() > 0) {
                stmt.setQueryTimeout(options.getQueryTimeout());
            }

            return stmt;
        } catch (SQLException e) {
            stmt.close();
            throw e;
        }
    }

    private void prepareStatement(int index, SpatialObject object, PreparedStatement stmt) throws SQLException {
        try {
            setGeometryOrNull(stmt, index, adapter.getGeometryAdapter()
                    .getGeometry(object, false, stmt.getConnection()));
        } catch (Exception e) {
            throw new SQLException("Failed to convert spatial object to database representation.", e);
        }
    }

    public long count(Select select) throws SQLException {
        return count(select, StatementOptions.defaults());
    }

    public long count(Select select, StatementOptions options) throws SQLException {
        try (Connection connection = adapter.getPool().getConnection()) {
            return count(select, connection, options);
        }
    }

    public long count(Select select, Connection connection) throws SQLException {
        return count(select, connection, StatementOptions.defaults());
    }

    public long count(Select select, Connection connection, StatementOptions options) throws SQLException {
        Table table = Table.of(Select.of(select)
                .removeOrderBy()
                .removeOffset()
                .removeFetch());
        Select count = Select.newInstance()
                .select(Function.of("count", WildcardColumn.newInstance()))
                .from(table);

        Objects.requireNonNull(options, "The statement options must not be null.");
        try (PreparedStatement stmt = prepareStatement(count, connection, StatementOptions.of(options).fetchSize(0));
             ResultSet rs = stmt.executeQuery()) {
            return rs.next() ? rs.getLong(1) : 0;
        }
    }

    public String toSql(SqlObject object, SqlBuildOptions options) {
        options.setPlaceholderBuilder((placeholder, op) -> {
            Object value = placeholder.getValue().orElse(null);
            return switch (value) {
                case Boolean booleanValue -> booleanValue ? "1" : "0";
                case String string -> "'" + string + "'";
                case Date date -> "'" + date + "'";
                case Timestamp timestamp -> "'" + timestamp.toInstant()
                        .atOffset(ZoneOffset.UTC)
                        .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME) + "'";
                case SpatialObject spatialObject -> toSql(spatialObject);
                case null, default -> String.valueOf(value);
            };
        });

        return SqlBuilder.newInstance().build(object, options);
    }

    private String toSql(SpatialObject object) {
        try {
            return adapter.getGeometryAdapter().getAsText(object);
        } catch (GeometryException e) {
            return null;
        }
    }
}
