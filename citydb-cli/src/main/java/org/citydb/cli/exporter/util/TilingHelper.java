/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.cli.exporter.util;

import org.citydb.cli.ExecutionException;
import org.citydb.cli.util.TokenReplacer;
import org.citydb.database.adapter.DatabaseAdapter;
import org.citydb.database.util.TempTable;
import org.citydb.model.common.Namespaces;
import org.citydb.model.geometry.Coordinate;
import org.citydb.model.geometry.Envelope;
import org.citydb.query.Query;
import org.citydb.query.builder.QueryBuildException;
import org.citydb.query.builder.sql.SqlBuildOptions;
import org.citydb.query.builder.sql.TempTableFilter;
import org.citydb.query.executor.QueryExecutor;
import org.citydb.query.filter.Filter;
import org.citydb.query.filter.common.Predicate;
import org.citydb.query.filter.literal.BBoxLiteral;
import org.citydb.query.filter.literal.PropertyRef;
import org.citydb.query.filter.operation.Operators;
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

public class TilingHelper implements AutoCloseable {
    private static final Tiling NO_TILING = Tiling.newInstance()
            .setExtent(Envelope.of(
                    Coordinate.of(-Double.MAX_VALUE, -Double.MAX_VALUE),
                    Coordinate.of(Double.MAX_VALUE, Double.MAX_VALUE)))
            .setScheme(MatrixScheme.of(1, 1));

    private final Logger logger = LoggerFactory.getLogger(TilingHelper.class);
    private final Tiling tiling;
    private final Query query;
    private final Connection connection;
    private final DatabaseAdapter adapter;
    private final boolean useTiling;
    private TileMatrix tileMatrix;
    private Envelope queryExtent;
    private TempTable pageTable;
    private boolean closed;

    private TilingHelper(Tiling tiling, Query query, Connection connection, DatabaseAdapter adapter) {
        this.tiling = tiling;
        this.query = query;
        this.connection = connection;
        this.adapter = adapter;
        useTiling = tiling != NO_TILING;
    }

    public static TilingHelper of(Tiling tiling, Query query, Connection connection, DatabaseAdapter adapter) throws ExecutionException {
        TilingHelper tilingHelper = new TilingHelper(tiling, query, connection, adapter);
        try {
            return tilingHelper.createPageTable().buildTileMatrix();
        } catch (Throwable e) {
            tilingHelper.close();
            throw e;
        }
    }

    public static Tiling noTiling() {
        return NO_TILING;
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

    private TilingHelper createPageTable() throws ExecutionException {
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

    private TilingHelper buildTileMatrix() throws ExecutionException {
        if (tiling.getExtent().isEmpty()) {
            logger.info("Computing tiling extent...");
            try {
                Query extentQuery = Query.of(query)
                        .setSorting(null)
                        .setCountLimit(null);

                queryExtent = QueryExecutor.builder(adapter)
                        .connection(connection)
                        .build(extentQuery, getTileBuildOptions(SqlBuildOptions.defaults().omitDistinct(true)))
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

    public QueryExecutor getQueryExecutor(Tile tile, SqlBuildOptions options, Path tempDirectory) throws ExecutionException {
        if (closed) {
            throw new ExecutionException("The tiling helper has already been closed.");
        }

        try {
            return QueryExecutor.builder(adapter)
                    .tempDirectory(tempDirectory)
                    .connection(connection)
                    .build(getTileQuery(tile), getTileBuildOptions(options));
        } catch (QueryBuildException e) {
            throw new ExecutionException("Failed to build database query.", e);
        }
    }

    private Query getTileQuery(Tile tile) {
        if (useTiling) {
            Query tileQuery = pageTable != null
                    ? Query.of(query).setCountLimit(null)
                    : Query.of(query);

            Predicate bboxFilter = PropertyRef.of("envelope", Namespaces.CORE)
                    .intersects(BBoxLiteral.of(tile.getExtent()));
            return tileQuery.setFilter(query.getFilter()
                    .map(filter -> Filter.of(Operators.and(bboxFilter, filter.getExpression())))
                    .orElse(Filter.of(bboxFilter)));
        } else {
            return query;
        }
    }

    private SqlBuildOptions getTileBuildOptions(SqlBuildOptions options) {
        return pageTable != null
                ? options.withTempTableFilter(TempTableFilter.of(pageTable, "id"))
                : options;
    }

    private boolean isPaginated() {
        return query.getCountLimit()
                .map(limit -> limit.getLimit().isPresent() || limit.getStartIndex().isPresent())
                .orElse(false);
    }

    public Path getOutputFile(Path outputFile, Tile tile) {
        if (useTiling) {
            String input = outputFile.toString();
            String resolved = TokenReplacer.replaceTileTokens(input, tile);

            if (!resolved.equals(input)) {
                return Path.of(resolved);
            } else if (tileMatrix == null || tileMatrix.size() > 1) {
                return getDefaultOutputFile(outputFile, tile);
            }
        }

        return outputFile;
    }

    private Path getDefaultOutputFile(Path file, Tile tile) {
        String suffix = "_" + tile.getColumn() + "_" + tile.getRow();
        String fileName = file.getFileName().toString();
        int index = fileName.lastIndexOf('.');
        return file.resolveSibling(index > 0
                ? fileName.substring(0, index) + suffix + fileName.substring(index)
                : fileName + suffix);
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
        }
    }
}
