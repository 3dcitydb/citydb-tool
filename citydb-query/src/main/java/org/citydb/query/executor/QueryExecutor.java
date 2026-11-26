/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.query.executor;

import org.citydb.database.adapter.DatabaseAdapter;
import org.citydb.database.geometry.GeometryException;
import org.citydb.database.srs.SpatialReference;
import org.citydb.database.util.SqlHelper;
import org.citydb.database.util.StatementOptions;
import org.citydb.database.util.TempTable;
import org.citydb.model.geometry.Envelope;
import org.citydb.query.Query;
import org.citydb.query.builder.QueryBuildException;
import org.citydb.query.builder.sql.SqlBuildOptions;
import org.citydb.query.builder.sql.SqlQueryBuilder;
import org.citydb.query.lod.LodFilter;
import org.citydb.query.lod.LodPlan;
import org.citydb.sqlbuilder.insert.Insert;
import org.citydb.sqlbuilder.query.Select;
import org.citydb.sqlbuilder.schema.Table;

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
    private final Connection connection;
    private final int queryTimeout;
    private final DatabaseAdapter adapter;
    private final SqlHelper helper;

    private QueryExecutor(Select select, Select count, Select extent, boolean deduplicateIds, Path tempDirectory,
                          Connection connection, int queryTimeout, DatabaseAdapter adapter) {
        this.select = select;
        this.count = count;
        this.extent = extent;
        this.deduplicateIds = deduplicateIds;
        this.tempDirectory = tempDirectory;
        this.connection = connection;
        this.queryTimeout = queryTimeout;
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
        return connection != null
                ? helper.count(count, connection, getStatementOptions())
                : helper.count(count, getStatementOptions());
    }

    public long countHits(Connection connection) throws SQLException {
        return helper.count(count, connection, getStatementOptions());
    }

    public Envelope computeExtent() throws GeometryException, SQLException {
        if (connection != null) {
            return computeExtent(connection);
        } else {
            try (Connection connection = adapter.getPool().getConnection()) {
                return computeExtent(connection);
            }
        }
    }

    public Envelope computeExtent(Connection connection) throws GeometryException, SQLException {
        try (PreparedStatement stmt = helper.prepareStatement(extent, connection, getStatementOptions().fetchSize(0));
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

    public long insertIdsInto(TempTable tempTable, String column) throws SQLException {
        TempTable.DataType type = tempTable.getDataType(column);
        if (type == null) {
            throw new SQLException("The column '" + column + "' is not defined for the temporary table " +
                    tempTable.getQualifiedName() + ".");
        } else if (type != TempTable.DataType.LONG) {
            throw new SQLException("The column '" + column + "' of the temporary table " +
                    tempTable.getQualifiedName() + " must be of type " + TempTable.DataType.LONG + " to store " +
                    "feature ids, but is of type " + type + ".");
        }

        Select features = Select.of(select);
        if (features.getOffset().isEmpty() && features.getFetch().isEmpty()) {
            features.removeOrderBy();
        }

        Table table = Table.of(features);
        Insert insert = Insert.into(tempTable.getTable())
                .columns(column)
                .query(Select.newInstance()
                        .distinct(deduplicateIds)
                        .select(table.column("id"))
                        .from(table));

        try (PreparedStatement stmt = helper.prepareStatement(insert, tempTable.getConnection(),
                getStatementOptions())) {
            return stmt.executeUpdate();
        }
    }

    public QueryResult executeQuery() throws SQLException {
        return connection != null
                ? executeQuery(connection, false)
                : executeQuery(adapter.getPool().getConnection(), true);
    }

    public QueryResult executeQuery(Connection connection) throws SQLException {
        return executeQuery(connection, false);
    }

    private QueryResult executeQuery(Connection connection, boolean ownsConnection) throws SQLException {
        PreparedStatement stmt = null;
        try {
            stmt = helper.prepareStatement(select, connection, getStatementOptions());
            return new QueryResult(connection, stmt, deduplicateIds, tempDirectory, ownsConnection);
        } catch (SQLException e) {
            try (Connection connectionToClose = ownsConnection ? connection : null;
                 PreparedStatement stmtToClose = stmt) {
                throw e;
            }
        }
    }

    private StatementOptions getStatementOptions() {
        return StatementOptions.defaults().queryTimeout(queryTimeout);
    }

    public static class Builder {
        private final DatabaseAdapter adapter;
        private final SqlQueryBuilder builder;
        private Path tempDirectory;
        private Connection connection;
        private int queryTimeout;
        private LodPlanner lodPlanner = LodPlanner.defaults();

        private Builder(DatabaseAdapter adapter) {
            this.adapter = Objects.requireNonNull(adapter, "The database adapter must not be null.");
            builder = SqlQueryBuilder.of(adapter);
        }

        public Builder tempDirectory(Path tempDirectory) {
            this.tempDirectory = tempDirectory;
            return this;
        }

        public Builder connection(Connection connection) {
            this.connection = connection;
            return this;
        }

        public Builder queryTimeout(int queryTimeout) {
            this.queryTimeout = Math.max(queryTimeout, 0);
            return this;
        }

        public Builder lodPlanner(LodPlanner lodPlanner) {
            this.lodPlanner = lodPlanner != null ? lodPlanner : LodPlanner.defaults();
            return this;
        }

        public QueryExecutor build(Query query) throws QueryBuildException {
            return build(query, SqlBuildOptions.defaults());
        }

        public QueryExecutor build(Query query, SqlBuildOptions options) throws QueryBuildException {
            LodPlan lodPlan = options.getLodPlan().isPresent()
                    ? options.getLodPlan().get()
                    : planLodFilter(query);
            options = SqlBuildOptions.of(options).lodPlan(lodPlan);
            SqlBuildOptions selectOptions = SqlBuildOptions.of(options)
                    .omitDistinct(options.isOmitDistinct() && !isPaginated(query));

            Select select = builder.build(query, selectOptions);
            Select count = builder.build(Query.of(query)
                            .setSorting(null)
                            .setCountLimit(null),
                    SqlBuildOptions.defaults().lodPlan(lodPlan));
            Select extent = buildExtentQuery(query, options);

            boolean deduplicateIds = selectOptions.isOmitDistinct()
                    && !select.toSql().equals(builder.build(Query.of(query),
                    SqlBuildOptions.of(selectOptions).omitDistinct(false)).toSql());

            return new QueryExecutor(select, count, extent, deduplicateIds, tempDirectory, connection, queryTimeout,
                    adapter);
        }

        private LodPlan planLodFilter(Query query) throws QueryBuildException {
            LodFilter lodFilter = query.getLodFilter().orElse(null);
            if (lodFilter == null) {
                return LodPlan.empty();
            }

            try {
                // without a decision, all LoD groups are searched top-down
                if (connection != null) {
                    return lodPlanner.plan(lodFilter, adapter, connection).orElse(LodPlan.empty());
                } else {
                    try (Connection connection = adapter.getPool().getConnection()) {
                        return lodPlanner.plan(lodFilter, adapter, connection).orElse(LodPlan.empty());
                    }
                }
            } catch (SQLException e) {
                throw new QueryBuildException("Failed to plan the LoD filter.", e);
            }
        }

        private Select buildExtentQuery(Query query, SqlBuildOptions options) throws QueryBuildException {
            boolean paginated = isPaginated(query);
            Query extentQuery = !paginated
                    ? Query.of(query).setSorting(null)
                    : query;

            Table features = Table.of(builder.build(extentQuery, SqlBuildOptions.of(options)
                    .omitDistinct(!paginated)
                    .withColumn("envelope")));

            return Select.newInstance()
                    .select(adapter.getGeometryAdapter()
                            .getSpatialOperationHelper()
                            .extent(features.column("envelope"))
                            .as("extent"))
                    .from(features);
        }

        private boolean isPaginated(Query query) {
            return query.getCountLimit()
                    .map(limit -> limit.getLimit().isPresent() || limit.getStartIndex().isPresent())
                    .orElse(false);
        }
    }
}
