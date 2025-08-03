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

package org.citydb.cli.exporter.util;

import org.apache.logging.log4j.Logger;
import org.citydb.cli.ExecutionException;
import org.citydb.cli.logging.LoggerManager;
import org.citydb.core.tuple.SimplePair;
import org.citydb.database.adapter.DatabaseAdapter;
import org.citydb.model.common.Namespaces;
import org.citydb.model.geometry.Coordinate;
import org.citydb.model.geometry.Envelope;
import org.citydb.query.Query;
import org.citydb.query.builder.sql.SqlBuildOptions;
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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TilingHelper {
    private static final Tiling NO_TILING = Tiling.newInstance()
            .setExtent(Envelope.of(
                    Coordinate.of(-Double.MAX_VALUE, -Double.MAX_VALUE),
                    Coordinate.of(Double.MAX_VALUE, Double.MAX_VALUE)))
            .setScheme(MatrixScheme.of(1, 1));

    private final Logger logger = LoggerManager.getInstance().getLogger(TilingHelper.class);
    private final Tiling tiling;
    private final Query query;
    private final DatabaseAdapter adapter;
    private final boolean useTiling;
    private TileMatrix tileMatrix;
    private Envelope queryExtent;

    private final Pattern tokenPattern = Pattern.compile("@(?:column|row|x_min|y_min|x_max|y_max)(?:,.+?)?@",
            Pattern.CASE_INSENSITIVE);
    private final Matcher matcher = Pattern.compile("").matcher("");

    private TilingHelper(Tiling tiling, Query query, DatabaseAdapter adapter) {
        this.tiling = tiling;
        this.query = query;
        this.adapter = adapter;
        useTiling = tiling != NO_TILING;
    }

    public static TilingHelper of(Tiling tiling, Query query, DatabaseAdapter adapter) throws ExecutionException {
        return new TilingHelper(tiling, query, adapter).buildTileMatrix();
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

    private TilingHelper buildTileMatrix() throws ExecutionException {
        if (tiling.getExtent().isEmpty()) {
            logger.info("Computing the extent of all features matching the request...");
            try {
                queryExtent = QueryExecutor.builder(adapter)
                        .build(query, SqlBuildOptions.defaults().omitDistinct(true))
                        .computeExtent();
                tiling.setExtent(queryExtent);
            } catch (Exception e) {
                throw new ExecutionException("Failed to compute the extent.", e);
            }
        }

        try {
            tileMatrix = tiling.buildTileMatrix(adapter);
            return this;
        } catch (TilingException e) {
            throw new ExecutionException("Failed to build the tile matrix.", e);
        }
    }

    public Query getTileQuery(Tile tile) {
        if (useTiling) {
            Predicate bboxFilter = PropertyRef.of("envelope", Namespaces.CORE)
                    .intersects(BBoxLiteral.of(tile.getExtent()));
            return Query.of(query).setFilter(query.getFilter()
                    .map(filter -> Filter.of(Operators.and(bboxFilter, filter.getExpression())))
                    .orElse(Filter.of(bboxFilter)));
        } else {
            return query;
        }
    }

    public Path getOutputFile(Path outputFile, Tile tile) {
        if (useTiling) {
            String file = outputFile.toString();
            List<SimplePair<String>> replacements = new ArrayList<>();

            matcher.reset(file).usePattern(tokenPattern);
            while (matcher.find()) {
                replacements.add(replaceToken(matcher.group(0), tile));
            }

            if (!replacements.isEmpty()) {
                for (SimplePair<String> replacement : replacements) {
                    file = file.replaceFirst(replacement.first(), replacement.second());
                }
            } else if (tileMatrix == null || tileMatrix.size() > 1) {
                file = getDefaultOutputFile(file, tile);
            }

            return Path.of(file);
        } else {
            return outputFile;
        }
    }

    private SimplePair<String> replaceToken(String token, Tile tile) {
        String[] parts = token.substring(1, token.length() - 1).split(",");
        String format = parts.length == 2 ? parts[1].trim() : "%s";
        String replacement = String.format(Locale.ENGLISH, format,
                switch (parts[0].toLowerCase(Locale.ROOT)) {
                    case "column" -> tile.getColumn();
                    case "row" -> tile.getRow();
                    case "x_min" -> tile.getExtent().getLowerCorner().getX();
                    case "y_min" -> tile.getExtent().getLowerCorner().getY();
                    case "x_max" -> tile.getExtent().getUpperCorner().getX();
                    case "y_max" -> tile.getExtent().getUpperCorner().getY();
                    default -> parts[0];
                });

        return SimplePair.of(token, replacement);
    }

    private String getDefaultOutputFile(String file, Tile tile) {
        String suffix = "_" + tile.getColumn() + "_" + tile.getRow();
        int index = file.lastIndexOf('.');
        return index > 0 ?
                file.substring(0, index) + suffix + "." + file.substring(index + 1) :
                file + suffix;
    }
}
