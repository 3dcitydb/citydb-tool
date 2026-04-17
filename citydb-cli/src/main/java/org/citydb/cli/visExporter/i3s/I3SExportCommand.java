/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
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
import org.citydb.vis.writer.i3s.I3SFormatOptions;
import picocli.CommandLine;

@CommandLine.Command(
        name = "i3s",
        description = "Export data in OGC I3S format.")
public class I3SExportCommand extends VisExportController {
    @CommandLine.Option(names = "--slpk",
            description = "Package the I3S output as a Scene Layer Package (.slpk) file, " +
                    "compatible with ArcGIS Pro. By default, I3S is exported as a folder structure. " +
                    "Recommended output file extension: .slpk")
    private boolean slpk;

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

        applySceneOptions(options);

        if (Command.hasMatchedOption("--slpk", commandSpec)) {
            options.setSlpk(slpk);
        }

        return options;
    }
}
