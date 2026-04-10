/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.cli.exporter.vis;

import org.citydb.cli.ExecutionException;
import org.citydb.cli.common.Command;
import org.citydb.cli.exporter.ExportController;
import org.citydb.cli.exporter.ExportOptions;
import org.citydb.cli.exporter.util.TilingHelper;
import org.citydb.config.ConfigException;
import org.citydb.config.common.ConfigObject;
import org.citydb.config.common.SrsReference;
import org.citydb.database.DatabaseManager;
import org.citydb.io.IOAdapter;
import org.citydb.io.IOAdapterManager;
import org.citydb.io.writer.WriteOptions;
import org.citydb.io.writer.options.OutputFormatOptions;
import org.citydb.operation.exporter.options.AppearanceOptions;
import org.citydb.query.Query;
import org.citydb.query.builder.QueryBuildException;
import org.citydb.query.executor.QueryExecutor;
import org.citydb.util.tiling.Tiling;
import org.citydb.vis.I3SAdapter;
import org.citydb.vis.I3SFormatOptions;
import picocli.CommandLine;

import java.io.IOException;
import java.sql.SQLException;

@CommandLine.Command(
        name = "i3s",
        description = "Export data in OGC I3S format.")
public class VisExportCommand extends ExportController {

    /** Target features per staging-directory bucket. */
    private static final int FEATURES_PER_TEXTURE_BUCKET = 1000;

    @CommandLine.Option(names = "--max-features-per-node", paramLabel = "<count>",
            defaultValue = "64",
            description = "Maximum number of features per I3S node (default: ${DEFAULT-VALUE}).")
    private int maxFeaturesPerNode;

    @CommandLine.Option(names = "--max-tree-depth", paramLabel = "<depth>",
            defaultValue = "16",
            description = "Maximum depth of the I3S node tree (default: ${DEFAULT-VALUE}).")
    private int maxTreeDepth;

    @CommandLine.Option(names = "--clamp-to-ground",
            description = "Place each building on the ellipsoid surface (height 0). " +
                    "Useful when no terrain is loaded in the viewer.")
    private boolean clampToGround;

    @CommandLine.Option(names = "--texture-scale", paramLabel = "<factor>",
            defaultValue = "1.0",
            description = "Texture resolution scale factor between 0.01 and 1.0 (default: ${DEFAULT-VALUE}). " +
                    "Lower values reduce texture size and improve loading speed in the viewer.")
    private double textureScale;

    @CommandLine.Spec
    private CommandLine.Model.CommandSpec commandSpec;

    @Override
    protected ExportOptions getExportOptions() throws ExecutionException {
        ExportOptions exportOptions = super.getExportOptions();

        // Force EPSG:4326 — Cesium only supports WGS84 for I3S layers.
        // Warn if the user explicitly configured a different target SRS
        // (CLI --target-srs or config file) so it's clear we overrode it.
        SrsReference originalSrs = exportOptions.getTargetSrs().orElse(null);
        if (originalSrs != null
                && originalSrs.getSRID().filter(srid -> srid == 4326).isEmpty()) {
            logger.warn("Custom target SRS is not supported for I3S export and will be " +
                    "overridden to EPSG:4326. Cesium only supports WGS84 for I3S layers.");
        }
        exportOptions.setTargetSrs(SrsReference.of(4326));

        // Affine transforms operate on the exported coordinates. Since I3S
        // forces output to WGS84 lon/lat, applying a user-supplied transform
        // would warp the geometry into nonsense coordinates that Cesium cannot
        // render correctly. Drop it.
        if (exportOptions.getAffineTransform().isPresent()) {
            logger.warn("Affine transform is not supported for I3S export and will be ignored. " +
                    "I3S geometries must be in WGS84 lon/lat for Cesium to render correctly.");
            exportOptions.setAffineTransform(null);
        }

        return exportOptions;
    }

    @Override
    protected Query getQuery(ExportOptions exportOptions) throws ExecutionException {
        Query query = super.getQuery(exportOptions);
        // Query sorting wraps the writer in SequentialWriter, which serializes
        // the export pipeline to enforce strict feature ordering. I3S does its
        // own spatial reordering in the close phase, so any input ordering is
        // discarded — sorting is pure overhead. Drop it.
        if (query.getSorting().isPresent()) {
            logger.warn("Query sorting is not supported for I3S export and will be ignored. " +
                    "I3S applies its own spatial ordering during scene-layer construction.");
            query.setSorting(null);
        }
        return query;
    }

    @Override
    protected Tiling getTiling(ExportOptions exportOptions) {
        // I3S already provides hierarchical spatial indexing through its own
        // LoD nodepage tree. External tiling would split the dataset into
        // disconnected scene layers, break LoD continuity at tile boundaries
        // and duplicate shared textures across tile sessions. Force-disable it
        // even when the user has explicitly requested tiling.
        if (tilingOptions != null || exportOptions.getTiling().isPresent()) {
            logger.warn("Tiling is not supported for I3S export and will be ignored. " +
                    "I3S already provides hierarchical spatial indexing (LoD nodepages); " +
                    "external tiling would break LoD continuity at tile boundaries.");
        }
        return TilingHelper.noTiling();
    }

    @Override
    protected void initialize(ExportOptions exportOptions, WriteOptions writeOptions,
                              DatabaseManager databaseManager) throws ExecutionException {
        super.initialize(exportOptions, writeOptions, databaseManager);

        // Bucket exported texture files into appearance/<n>/ subfolders so the
        // staging directory does not blow up to tens of thousands of small
        // files (which cripples NTFS / Windows Defender on large exports).
        // The bucket count is sized from the feature count so we get
        // ~FEATURES_PER_TEXTURE_BUCKET files per bucket regardless of dataset size.
        long featureCount;
        try {
            featureCount = QueryExecutor.builder(databaseManager.getAdapter())
                    .build(getQuery(exportOptions))
                    .countHits();
        } catch (QueryBuildException | SQLException | IOException e) {
            throw new ExecutionException("Failed to count features for texture-bucket sizing.", e);
        }
        int buckets = (int) ((featureCount + FEATURES_PER_TEXTURE_BUCKET - 1)
                / FEATURES_PER_TEXTURE_BUCKET);
        logger.debug("Sized texture buckets to {} for {} features.", buckets, featureCount);

        AppearanceOptions appearanceOptions = exportOptions.getAppearanceOptions()
                .orElseGet(AppearanceOptions::new);
        appearanceOptions.setNumberOfTextureBuckets(buckets);
        // Stage textures inside the writer's .tmp/ directory so the entire
        // intermediate footprint (mesh/attr stores + textures) can be cleaned
        // up by removing a single subfolder of the output directory.
        appearanceOptions.setTextureOutputFolder(".tmp/i3s-textures");
        exportOptions.setAppearanceOptions(appearanceOptions);
    }

    @Override
    protected IOAdapter getIOAdapter(IOAdapterManager ioManager) {
        return ioManager.getAdapter(I3SAdapter.class);
    }

    @Override
    protected OutputFormatOptions getFormatOptions(ConfigObject<OutputFormatOptions> formatOptions)
            throws ExecutionException {
        I3SFormatOptions options;
        try {
            options = formatOptions.getOrElse(I3SFormatOptions.class, I3SFormatOptions::new);
        } catch (ConfigException e) {
            throw new ExecutionException("Failed to get I3S format options from config.", e);
        }

        if (Command.hasMatchedOption("--max-features-per-node", commandSpec)) {
            options.setMaxFeaturesPerNode(maxFeaturesPerNode);
        }

        if (Command.hasMatchedOption("--max-tree-depth", commandSpec)) {
            options.setMaxTreeDepth(maxTreeDepth);
        }

        if (Command.hasMatchedOption("--clamp-to-ground", commandSpec)) {
            options.setClampToGround(clampToGround);
        }

        if (Command.hasMatchedOption("--texture-scale", commandSpec)) {
            options.setTextureScale(textureScale);
        }

        return options;
    }
}
