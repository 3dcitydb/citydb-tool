/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.cli.exporter;

import org.citydb.cli.ExecutionException;
import org.citydb.database.adapter.DatabaseAdapter;
import org.citydb.database.util.TempTable;
import org.citydb.model.common.Namespaces;
import org.citydb.model.geometry.Coordinate;
import org.citydb.model.geometry.Envelope;
import org.citydb.query.Query;
import org.citydb.query.builder.QueryBuildException;
import org.citydb.query.builder.sql.SqlBuildOptions;
import org.citydb.query.executor.QueryExecutor;
import org.citydb.query.filter.Filter;
import org.citydb.query.filter.common.Predicate;
import org.citydb.query.filter.literal.BBoxLiteral;
import org.citydb.query.filter.literal.PropertyRef;
import org.citydb.query.filter.operation.Operators;
import org.citydb.query.temptable.TempTableFilter;
import org.citydb.util.tiling.Tile;
import org.citydb.util.tiling.TileMatrix;
import org.citydb.util.tiling.Tiling;
import org.citydb.util.tiling.TilingException;
import org.citydb.util.tiling.options.MatrixScheme;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Optional;

public class ExportQueryHelper implements AutoCloseable {
    private final Logger logger = LoggerFactory.getLogger(ExportQueryHelper.class);
    private final Tiling tiling;
    private final Query query;
    private final DatabaseAdapter adapter;
    private final boolean useTiling;
    private Connection connection;
    private TileMatrix tileMatrix;
    private Envelope queryExtent;
    private TempTable pageTable;
    private boolean closed;

    private ExportQueryHelper(Tiling tiling, Query query, DatabaseAdapter adapter) {
        this.tiling = tiling != null ? tiling : noTiling();
        this.query = query;
        this.adapter = adapter;
        useTiling = tiling != null;
    }

    static ExportQueryHelper of(Tiling tiling, Query query, DatabaseAdapter adapter) throws ExecutionException {
        ExportQueryHelper queryHelper = new ExportQueryHelper(tiling, query, adapter);
        try {
            return queryHelper.createConnection()
                    .createPageTable()
                    .buildTileMatrix();
        } catch (Throwable e) {
            queryHelper.close();
            throw e;
        }
    }

    public boolean isUseTiling() {
        return useTiling;
    }

    public TileMatrix getTileMatrix() {
        return tileMatrix;
    }

    public Optional<Envelope> getQueryExtent() {
        return Optional.ofNullable(queryExtent);
    }

    public QueryContext getQueryContext(Tile tile, SqlBuildOptions options, Path tempDirectory) throws ExecutionException {
        if (closed) {
            throw new ExecutionException("The export query helper has already been closed.");
        }

        try {
            QueryExecutor executor = QueryExecutor.builder(adapter)
                    .tempDirectory(tempDirectory)
                    .connection(connection)
                    .build(getTileQuery(tile), options);
            return new QueryContext(tile, executor, connection);
        } catch (QueryBuildException e) {
            throw new ExecutionException("Failed to build database query.", e);
        }
    }

    private ExportQueryHelper createConnection() throws ExecutionException {
        try {
            connection = adapter.getPool().getConnection(false);
            return this;
        } catch (SQLException e) {
            throw new ExecutionException("Failed to get a database connection.", e);
        }
    }

    private ExportQueryHelper createPageTable() throws ExecutionException {
        if (useTiling && isPaginated()) {
            logger.info("Querying features of the requested page...");
            try {
                pageTable = TempTable.builder(adapter)
                        .longColumn("id")
                        .index("id")
                        .create(connection);

                long features = QueryExecutor.builder(adapter)
                        .build(query, SqlBuildOptions.defaults())
                        .insertIdsInto(pageTable, "id");
                logger.debug("Stored {} feature(s) of the requested page in temporary table {}.", features,
                        pageTable.getQualifiedName());
            } catch (Exception e) {
                throw new ExecutionException("Failed to query the features of the requested page.", e);
            }
        }

        return this;
    }

    private ExportQueryHelper buildTileMatrix() throws ExecutionException {
        if (tiling.getExtent().isEmpty()) {
            logger.info("Computing tiling extent...");
            try {
                Query extentQuery = restrictToPage(Query.of(query)
                        .setSorting(null)
                        .setCountLimit(null));

                queryExtent = QueryExecutor.builder(adapter)
                        .connection(connection)
                        .build(extentQuery, SqlBuildOptions.defaults().omitDistinct(true))
                        .computeExtent();
                tiling.setExtent(queryExtent);
            } catch (Exception e) {
                throw new ExecutionException("Failed to compute the tiling extent.", e);
            }
        }

        try {
            tileMatrix = tiling.buildTileMatrix(adapter);
            return this;
        } catch (TilingException e) {
            throw new ExecutionException("Failed to build the tile matrix.", e);
        }
    }

    private Query getTileQuery(Tile tile) {
        if (useTiling) {
            Query tileQuery = restrictToPage(Query.of(query));
            Predicate bboxFilter = PropertyRef.of("envelope", Namespaces.CORE)
                    .intersects(BBoxLiteral.of(tile.getExtent()));
            return tileQuery.setFilter(query.getFilter()
                    .map(filter -> Filter.of(Operators.and(bboxFilter, filter.getExpression())))
                    .orElse(Filter.of(bboxFilter)));
        } else {
            return query;
        }
    }

    private Query restrictToPage(Query query) {
        // the page table already restricts the features to the requested page
        return pageTable != null
                ? query.setCountLimit(null).addTempTableFilter(TempTableFilter.of(pageTable, "id"))
                : query;
    }

    private boolean isPaginated() {
        return query.getCountLimit()
                .map(limit -> limit.getLimit().isPresent() || limit.getStartIndex().isPresent())
                .orElse(false);
    }

    private Tiling noTiling() {
        return Tiling.newInstance()
                .setExtent(Envelope.of(
                        Coordinate.of(-Double.MAX_VALUE, -Double.MAX_VALUE),
                        Coordinate.of(Double.MAX_VALUE, Double.MAX_VALUE)))
                .setScheme(MatrixScheme.of(1, 1));
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            if (pageTable != null) {
                try {
                    pageTable.close();
                } catch (SQLException e) {
                    logger.warn("Failed to drop the temporary table {}.", pageTable.getQualifiedName(), e);
                }
            }

            if (connection != null) {
                try (Connection connection = this.connection) {
                    connection.rollback();
                } catch (SQLException e) {
                    logger.warn("Failed to release the database connection.", e);
                }
            }
        }
    }
}
