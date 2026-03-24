/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.cli.exporter.vis;

import org.citydb.cli.ExecutionException;
import org.citydb.cli.common.Command;
import org.citydb.cli.exporter.ExportController;
import org.citydb.cli.exporter.ExportOptions;
import org.citydb.config.ConfigException;
import org.citydb.config.common.ConfigObject;
import org.citydb.config.common.SrsReference;
import org.citydb.io.IOAdapter;
import org.citydb.io.IOAdapterManager;
import org.citydb.vis.I3SAdapter;
import org.citydb.vis.I3SFormatOptions;
import org.citydb.io.writer.options.OutputFormatOptions;
import picocli.CommandLine;

@CommandLine.Command(
        name = "i3s",
        description = "Export data in OGC I3S format.")
public class VisExportCommand extends ExportController {

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

    @CommandLine.Spec
    private CommandLine.Model.CommandSpec commandSpec;

    @Override
    protected ExportOptions getExportOptions() throws ExecutionException {
        ExportOptions exportOptions = super.getExportOptions();
        // Force EPSG:4326 — Cesium only supports WGS84 for I3S layers
        exportOptions.setTargetSrs(SrsReference.of(4326));
        return exportOptions;
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

        return options;
    }
}
