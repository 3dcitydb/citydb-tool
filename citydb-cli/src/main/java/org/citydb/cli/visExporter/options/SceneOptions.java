/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.cli.visExporter.options;

import org.citydb.cli.common.Option;
import picocli.CommandLine;

public class SceneOptions implements Option {
    @CommandLine.Option(names = "--max-features-per-node", paramLabel = "<count>",
            defaultValue = "64",
            description = "Maximum number of features per node (default: ${DEFAULT-VALUE}).")
    private int maxFeaturesPerNode;

    @CommandLine.Option(names = "--max-tree-depth", paramLabel = "<depth>",
            defaultValue = "16",
            description = "Maximum depth of the node tree (default: ${DEFAULT-VALUE}).")
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

    public int getMaxFeaturesPerNode() {
        return maxFeaturesPerNode;
    }

    public int getMaxTreeDepth() {
        return maxTreeDepth;
    }

    public boolean isClampToGround() {
        return clampToGround;
    }

    public double getTextureScale() {
        return textureScale;
    }

    @Override
    public void preprocess(CommandLine commandLine) {
        if (maxFeaturesPerNode <= 0) {
            throw new CommandLine.ParameterException(commandLine,
                    "Error: --max-features-per-node must be a positive integer but was '" +
                            maxFeaturesPerNode + "'");
        }

        if (maxTreeDepth <= 0) {
            throw new CommandLine.ParameterException(commandLine,
                    "Error: --max-tree-depth must be a positive integer but was '" +
                            maxTreeDepth + "'");
        }

        if (textureScale < 0.01 || textureScale > 1.0) {
            throw new CommandLine.ParameterException(commandLine,
                    "Error: --texture-scale must be between 0.01 and 1.0 but was '" +
                            textureScale + "'");
        }
    }
}
