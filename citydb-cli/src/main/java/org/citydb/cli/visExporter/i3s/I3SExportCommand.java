/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.cli.visExporter.i3s;

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
import org.citydb.vis.I3SAdapter;
import org.citydb.vis.I3SFormatOptions;
import picocli.CommandLine;

import java.io.IOException;
import java.sql.SQLException;

@CommandLine.Command(
        name = "i3s",
        description = "Export data in OGC I3S format.")
public class I3SExportCommand extends VisExportController {

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
            appearanceOptions.setTextureOutputFolder(".tmp/i3s-textures");
            exportOptions.setAppearanceOptions(appearanceOptions);
        }
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
