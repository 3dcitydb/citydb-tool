/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.query.executor;

import org.citydb.database.adapter.DatabaseAdapter;
import org.citydb.database.geometry.GeometryException;
import org.citydb.database.srs.SpatialReference;
import org.citydb.database.util.SqlHelper;
import org.citydb.model.geometry.Envelope;
import org.citydb.query.Query;
import org.citydb.query.builder.QueryBuildException;
import org.citydb.query.builder.sql.SqlBuildOptions;
import org.citydb.query.builder.sql.SqlQueryBuilder;
import org.citydb.sqlbuilder.query.Select;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;

public class QueryExecutor {
    private final Select select;
    private final Select count;
    private final Select extent;
    private final boolean deduplicateIds;
    private final Path tempDirectory;
    private final DatabaseAdapter adapter;
    private final SqlHelper helper;

    private QueryExecutor(Select select, Select count, Select extent, boolean deduplicateIds, Path tempDirectory, DatabaseAdapter adapter) {
        this.select = select;
        this.count = count;
        this.extent = extent;
        this.deduplicateIds = deduplicateIds;
        this.tempDirectory = tempDirectory;
        this.adapter = adapter;
        helper = adapter.getSchemaAdapter().getSqlHelper();
    }

    public static Builder builder(DatabaseAdapter adapter) {
        return new Builder(adapter);
    }

    public Select getSelect() {
        return select;
    }

    public long countHits() throws SQLException {
        return helper.count(count);
    }

    public long countHits(Connection connection) throws SQLException {
        return helper.count(count, connection);
    }

    public Envelope computeExtent() throws GeometryException, SQLException {
        try (Connection connection = adapter.getPool().getConnection()) {
            return computeExtent(connection);
        }
    }

    public Envelope computeExtent(Connection connection) throws GeometryException, SQLException {
        try (PreparedStatement stmt = helper.prepareStatement(extent, connection);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                Envelope envelope = adapter.getGeometryAdapter().getEnvelope(rs.getObject("extent"));
                if (envelope != null) {
                    SpatialReference reference = adapter.getDatabaseMetadata().getSpatialReference();
                    return envelope.setSRID(reference.getSRID())
                            .setSrsIdentifier(reference.getIdentifier());
                }
            }
        }

        return null;
    }

    public QueryResult executeQuery() throws SQLException {
        return executeQuery(adapter.getPool().getConnection(), true);
    }

    public QueryResult executeQuery(Connection connection) throws SQLException {
        return executeQuery(connection, false);
    }

    private QueryResult executeQuery(Connection connection, boolean closeConnection) throws SQLException {
        PreparedStatement stmt = null;
        try {
            stmt = helper.prepareStatement(select, connection);
            return new QueryResult(connection, stmt, deduplicateIds, tempDirectory, closeConnection);
        } catch (SQLException e) {
            try (Connection connectionToClose = closeConnection ? connection : null;
                 PreparedStatement stmtToClose = stmt) {
                throw e;
            }
        }
    }

    public static class Builder {
        private final DatabaseAdapter adapter;
        private final SqlQueryBuilder builder;
        private Path tempDirectory;

        private Builder(DatabaseAdapter adapter) {
            this.adapter = Objects.requireNonNull(adapter, "The database adapter must not be null.");
            builder = SqlQueryBuilder.of(adapter);
        }

        public Builder tempDirectory(Path tempDirectory) {
            this.tempDirectory = tempDirectory;
            return this;
        }

        public QueryExecutor build(Query query) throws QueryBuildException, IOException {
            return build(query, SqlBuildOptions.defaults());
        }

        public QueryExecutor build(Query query, SqlBuildOptions options) throws QueryBuildException {
            SqlBuildOptions selectOptions = SqlBuildOptions.of(options)
                    .omitDistinct(options.isOmitDistinct() && !isPaginated(query));

            Select select = builder.build(query, selectOptions);
            Select count = options.isOmitDistinct()
                    ? builder.build(query, SqlBuildOptions.of(options).omitDistinct(false))
                    : select;

            boolean deduplicateIds = options.isOmitDistinct() && !select.toSql().equals(count.toSql());
            Select extentQuery = buildExtentQuery(query, options);

            return new QueryExecutor(select, count, extentQuery, deduplicateIds, tempDirectory, adapter);
        }

        private Select buildExtentQuery(Query query, SqlBuildOptions options) throws QueryBuildException {
            Select extent = builder.build(query, SqlBuildOptions.of(options)
                            .omitDistinct(!isPaginated(query))
                            .withColumn("envelope"))
                    .removeSelect()
                    .removeOrderBy()
                    .removeGroupBy();

            return extent.select(adapter.getGeometryAdapter()
                    .getSpatialOperationHelper()
                    .extent(extent.getFrom().get(0).column("envelope"))
                    .as("extent"));
        }

        private boolean isPaginated(Query query) {
            return query.getCountLimit()
                    .map(limit -> limit.getLimit().isPresent() || limit.getStartIndex().isPresent())
                    .orElse(false);
        }
    }
}
