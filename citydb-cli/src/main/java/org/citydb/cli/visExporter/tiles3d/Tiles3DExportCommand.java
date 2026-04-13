/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.cli.visExporter.tiles3d;

import org.citydb.cli.ExecutionException;
import org.citydb.cli.common.Command;
import org.citydb.cli.visExporter.VisExportController;
import org.citydb.cli.visExporter.VisExportOptions;
import org.citydb.config.ConfigException;
import org.citydb.config.common.ConfigObject;
import org.citydb.database.DatabaseManager;
import org.citydb.io.IOAdapter;
import org.citydb.io.IOAdapterManager;
import org.citydb.io.writer.WriteOptions;
import org.citydb.io.writer.options.OutputFormatOptions;
import org.citydb.operation.exporter.options.AppearanceOptions;
import org.citydb.query.builder.QueryBuildException;
import org.citydb.query.executor.QueryExecutor;
import org.citydb.vis.Tiles3DAdapter;
import org.citydb.vis.writer.tiles3d.Tiles3DFormatOptions;
import picocli.CommandLine;

import java.io.IOException;
import java.sql.SQLException;

@CommandLine.Command(
        name = "3dtiles",
        description = "Export data in OGC 3D Tiles 1.1 format.")
public class Tiles3DExportCommand extends VisExportController {

    private static final int FEATURES_PER_TEXTURE_BUCKET = 1000;

    @CommandLine.Option(names = "--max-features-per-node", paramLabel = "<count>",
            defaultValue = "64",
            description = "Maximum number of features per tile node (default: ${DEFAULT-VALUE}).")
    private int maxFeaturesPerNode;

    @CommandLine.Option(names = "--max-tree-depth", paramLabel = "<depth>",
            defaultValue = "16",
            description = "Maximum depth of the tile tree (default: ${DEFAULT-VALUE}).")
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
    protected void initialize(VisExportOptions exportOptions, WriteOptions writeOptions,
                              DatabaseManager databaseManager) throws ExecutionException {
        if (exportOptions.getAppearanceOptions()
                .map(AppearanceOptions::isExportAppearances)
                .orElse(true)) {
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
            appearanceOptions.setTextureOutputFolder(".tmp/3dtiles-textures");
            exportOptions.setAppearanceOptions(appearanceOptions);
        }
    }

    @Override
    protected IOAdapter getIOAdapter(IOAdapterManager ioManager) {
        return ioManager.getAdapter(Tiles3DAdapter.class);
    }

    @Override
    protected OutputFormatOptions getFormatOptions(ConfigObject<OutputFormatOptions> formatOptions)
            throws ExecutionException {
        Tiles3DFormatOptions options;
        try {
            options = formatOptions.getOrElse(Tiles3DFormatOptions.class, Tiles3DFormatOptions::new);
        } catch (ConfigException e) {
            throw new ExecutionException("Failed to get 3D Tiles format options from config.", e);
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
