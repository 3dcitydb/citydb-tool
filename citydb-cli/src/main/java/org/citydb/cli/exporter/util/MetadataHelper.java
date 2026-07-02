/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.cli.exporter.util;

import org.citydb.cli.ExecutionException;
import org.citydb.cli.exporter.ExportOptions;
import org.citydb.cli.util.TokenReplacer;
import org.citydb.config.common.SrsReference;
import org.citydb.core.file.OutputFile;
import org.citydb.database.adapter.DatabaseAdapter;
import org.citydb.database.srs.SpatialReference;
import org.citydb.io.writer.WriteOptions;
import org.citydb.io.writer.metadata.Metadata;
import org.citydb.io.writer.options.MetadataOptions;
import org.citydb.model.geometry.Envelope;
import org.citydb.query.executor.QueryExecutor;
import org.citydb.util.tiling.Tile;
import org.citydb.util.tiling.Tiling;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MetadataHelper {
    private final Logger logger = LoggerFactory.getLogger(MetadataHelper.class);
    private final MetadataOptions metadataOptions;
    private final SrsReference targetSrs;
    private final DatabaseAdapter adapter;
    private final boolean useTiling;

    private MetadataHelper(ExportOptions exportOptions, WriteOptions writeOptions, Tiling tiling, DatabaseAdapter adapter) {
        this.adapter = adapter;
        metadataOptions = writeOptions.getMetadataOptions().orElse(null);
        targetSrs = exportOptions.getTargetSrs().orElse(null);
        useTiling = tiling != TilingHelper.noTiling();
    }

    public static MetadataHelper of(ExportOptions exportOptions, WriteOptions writeOptions, Tiling tiling, DatabaseAdapter adapter) {
        return new MetadataHelper(exportOptions, writeOptions, tiling, adapter);
    }

    public Metadata getMetadata(QueryExecutor executor, OutputFile outputFile, Tile tile) throws ExecutionException {
        Metadata metadata = new Metadata();
        if (metadataOptions == null) {
            return metadata;
        }

        metadataOptions.getTitle()
                .map(title -> replaceTokens(title, outputFile, tile))
                .ifPresent(metadata::setTitle);

        metadataOptions.getDescription()
                .map(description -> replaceTokens(description, outputFile, tile))
                .ifPresent(metadata::setDescription);

        if (metadataOptions.isComputeExtent()) {
            metadata.setExtent(computeExtent(executor, tile));
        }

        return metadata;
    }

    private String replaceTokens(String input, OutputFile outputFile, Tile tile) {
        input = TokenReplacer.replaceFileTokens(input, outputFile);
        if (useTiling) {
            input = TokenReplacer.replaceTileTokens(input, tile);
        }

        return input;
    }

    private Envelope computeExtent(QueryExecutor executor, Tile tile) throws ExecutionException {
        Envelope extent;
        try {
            logger.debug("Computing export extent...");
            extent = executor.computeExtent();
        } catch (Exception e) {
            throw new ExecutionException("Failed to compute the export extent.", e);
        }

        if (extent == null && useTiling) {
            logger.debug("No export extent available. Falling back to tile extent.");
            extent = tile.getExtent();
        }

        if (extent != null) {
            try {
                SpatialReference databaseSrs = adapter.getDatabaseMetadata().getSpatialReference();
                SpatialReference targetSrs = adapter.getGeometryAdapter().getSrsHelper()
                        .getSpatialReference(this.targetSrs)
                        .orElse(databaseSrs);
                if (extent.getSRID().orElse(databaseSrs.getSRID()) != targetSrs.getSRID()) {
                    extent = adapter.getGeometryAdapter().transform(extent, targetSrs.getSRID());
                }
            } catch (Exception e) {
                throw new RuntimeException("The requested target SRS is not supported.", e);
            }
        }

        return extent;
    }
}
