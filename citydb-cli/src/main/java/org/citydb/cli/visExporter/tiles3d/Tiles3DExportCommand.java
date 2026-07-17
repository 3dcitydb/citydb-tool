/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences <https://www.hft-stuttgart.de>
 */

package org.citydb.cli.visExporter.tiles3d;

import org.citydb.cli.ExecutionException;
import org.citydb.cli.common.Command;
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
    @CommandLine.Option(names = "--implicit-geometry-instancing",
            description = "Emit implicit geometries as GPU instances (glTF " +
                    "EXT_mesh_gpu_instancing + EXT_instance_features): each template mesh " +
                    "is stored once per tile and placed per instance. Instances whose " +
                    "transformation matrix cannot be expressed as rotation and scale " +
                    "(shear or mirroring) fall back to baked meshes automatically. " +
                    "When omitted, every implicit geometry is baked as a full mesh copy " +
                    "per occurrence — omit this option for consumers without support for " +
                    "the two glTF extensions.")
    private boolean implicitGeometryInstancing;

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

    @Override
    protected void applyAdditionalFormatOptions(Tiles3DFormatOptions options) {
        if (Command.hasMatchedOption("--implicit-geometry-instancing", commandSpec)) {
            options.setImplicitGeometryInstancing(implicitGeometryInstancing);
        }
    }
}
