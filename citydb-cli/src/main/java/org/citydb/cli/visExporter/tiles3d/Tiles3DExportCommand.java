/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences <https://www.hft-stuttgart.de>
 */

package org.citydb.cli.visExporter.tiles3d;

import org.citydb.cli.ExecutionException;
import org.citydb.cli.visExporter.VisExportController;
import org.citydb.config.ConfigException;
import org.citydb.config.common.ConfigObject;
import org.citydb.io.IOAdapter;
import org.citydb.io.IOAdapterManager;
import org.citydb.io.writer.options.OutputFormatOptions;
import org.citydb.vis.Tiles3DAdapter;
import org.citydb.vis.config.Tiles3DFormatOptions;
import picocli.CommandLine;

@CommandLine.Command(
        name = "3dtiles",
        description = "Export data in OGC 3D Tiles 1.1 format.")
public class Tiles3DExportCommand extends VisExportController<Tiles3DFormatOptions> {
    @Override
    protected IOAdapter getIOAdapter(IOAdapterManager ioManager) {
        return ioManager.getAdapter(Tiles3DAdapter.class);
    }

    @Override
    protected Tiles3DFormatOptions newFormatOptions(ConfigObject<OutputFormatOptions> formatOptions)
            throws ExecutionException {
        try {
            return formatOptions.getOrElse(Tiles3DFormatOptions.class, Tiles3DFormatOptions::new);
        } catch (ConfigException e) {
            throw new ExecutionException("Failed to get 3D Tiles format options from config.", e);
        }
    }
}
