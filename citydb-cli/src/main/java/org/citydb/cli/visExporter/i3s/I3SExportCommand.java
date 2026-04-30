/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences <https://www.hft-stuttgart.de>
 */

package org.citydb.cli.visExporter.i3s;

import org.citydb.cli.ExecutionException;
import org.citydb.cli.common.Command;
import org.citydb.cli.visExporter.VisExportController;
import org.citydb.config.ConfigException;
import org.citydb.config.common.ConfigObject;
import org.citydb.io.IOAdapter;
import org.citydb.io.IOAdapterManager;
import org.citydb.io.writer.options.OutputFormatOptions;
import org.citydb.vis.I3SAdapter;
import org.citydb.vis.config.I3SFormatOptions;
import picocli.CommandLine;

@CommandLine.Command(
        name = "i3s",
        description = "Export data in OGC I3S format.")
public class I3SExportCommand extends VisExportController<I3SFormatOptions> {
    @CommandLine.Option(names = "--slpk",
            description = "Package the I3S output as a Scene Layer Package (.slpk) file, " +
                    "compatible with ArcGIS Pro. By default, I3S is exported as a folder structure. " +
                    "Recommended output file extension: .slpk")
    private boolean slpk;

    @CommandLine.Option(names = "--obb",
            description = "Emit oriented bounding boxes (OBB) in node pages alongside MBS. " +
                    "Required by the ArcGIS Maps SDK for JavaScript and the ArcGIS Online Scene Viewer " +
                    "when loading a folder export; without it the layer loads but renders nothing. " +
                    "Suppressed by default in folder mode because CesiumJS mis-culls buildings when OBB " +
                    "is present. SLPK output always includes OBB regardless of this flag (ArcGIS Pro requires it).")
    private boolean obb;

    @Override
    protected IOAdapter getIOAdapter(IOAdapterManager ioManager) {
        return ioManager.getAdapter(I3SAdapter.class);
    }

    @Override
    protected I3SFormatOptions newFormatOptions(ConfigObject<OutputFormatOptions> formatOptions)
            throws ExecutionException {
        try {
            return formatOptions.getOrElse(I3SFormatOptions.class, I3SFormatOptions::new);
        } catch (ConfigException e) {
            throw new ExecutionException("Failed to get I3S format options from config.", e);
        }
    }

    @Override
    protected void applyAdditionalFormatOptions(I3SFormatOptions options) {
        if (Command.hasMatchedOption("--slpk", commandSpec)) {
            options.setSlpk(slpk);
        }
        if (Command.hasMatchedOption("--obb", commandSpec)) {
            options.setObb(obb);
        }
    }
}
