/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences <https://www.hft-stuttgart.de>
 */

package org.citydb.cli.visExporter.options;

import org.citydb.cli.common.Option;
import picocli.CommandLine;

public class SceneOptions implements Option {
    @CommandLine.Option(names = "--grid-edge-length", paramLabel = "<meters>",
            defaultValue = "200.0",
            description = "Edge length in meters of one grid cell used as the leaf of the " +
                    "spatial aggregation tree (default: ${DEFAULT-VALUE}). Smaller values " +
                    "produce a finer grid and shorter camera load distances.")
    private double gridEdgeLength;

    @CommandLine.Option(names = "--lod-refine-radius", paramLabel = "<pixels>",
            defaultValue = "128.0",
            description = "Projected MBS radius (pixels) above which a tile refines to its " +
                    "children (default: ${DEFAULT-VALUE}). Applied uniformly to both 3D Tiles " +
                    "(via geometric error) and I3S (via LOD threshold) so both formats load " +
                    "the same level of detail at any given camera distance. Lower values " +
                    "load more detail, higher values lighten the viewer.")
    private double lodRefineRadius;

    @CommandLine.Option(names = "--clamp-to-ground",
            description = "Place each building on the ellipsoid surface (height 0). " +
                    "Useful when no terrain is loaded in the viewer.")
    private boolean clampToGround;

    @CommandLine.Option(names = "--texture-scale", paramLabel = "<factor>",
            defaultValue = "1.0",
            description = "Texture resolution scale factor between 0.01 and 1.0 (default: ${DEFAULT-VALUE}). " +
                    "Lower values reduce texture size and improve loading speed in the viewer.")
    private double textureScale;

    @CommandLine.Option(names = "--max-atlas-size", paramLabel = "<pixels>",
            defaultValue = "1024",
            description = "Maximum texture atlas edge length in pixels, between 1024 and 16384 " +
                    "(default: ${DEFAULT-VALUE}). Higher values pack more textures per atlas " +
                    "but increase GPU memory usage and texture upload latency in the viewer " +
                    "(e.g., 2048² = 16 MB RGBA8 per atlas page, 4× more than 1024²).")
    private int maxAtlasSize;

    public double getGridEdgeLength() {
        return gridEdgeLength;
    }

    public double getLodRefineRadius() {
        return lodRefineRadius;
    }

    public boolean isClampToGround() {
        return clampToGround;
    }

    public double getTextureScale() {
        return textureScale;
    }

    public int getMaxAtlasSize() {
        return maxAtlasSize;
    }

    @Override
    public void preprocess(CommandLine commandLine) {
        if (gridEdgeLength <= 0) {
            throw new CommandLine.ParameterException(commandLine,
                    "Error: --grid-edge-length must be a positive number of meters but was '" +
                            gridEdgeLength + "'");
        }

        if (lodRefineRadius <= 0) {
            throw new CommandLine.ParameterException(commandLine,
                    "Error: --lod-refine-radius must be a positive number of pixels but was '" +
                            lodRefineRadius + "'");
        }

        if (textureScale < 0.01 || textureScale > 1.0) {
            throw new CommandLine.ParameterException(commandLine,
                    "Error: --texture-scale must be between 0.01 and 1.0 but was '" +
                            textureScale + "'");
        }

        if (maxAtlasSize < 1024 || maxAtlasSize > 16384) {
            throw new CommandLine.ParameterException(commandLine,
                    "Error: --max-atlas-size must be between 1024 and 16384 but was '" +
                            maxAtlasSize + "'");
        }
    }
}
