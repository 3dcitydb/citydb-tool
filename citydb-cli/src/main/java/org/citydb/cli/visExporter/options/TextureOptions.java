/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences <https://www.hft-stuttgart.de>
 */

package org.citydb.cli.visExporter.options;

import org.citydb.cli.common.Option;
import org.citydb.vis.appearance.AtlasFallbackStrategy;
import org.citydb.vis.appearance.AtlasOverflowMode;
import picocli.CommandLine;

public class TextureOptions implements Option {
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

    @CommandLine.Option(names = "--atlas-overflow-mode", paramLabel = "<mode>",
            defaultValue = "hybrid",
            description = "Strategy when a cell's textures exceed --max-atlas-size: " +
                    "${COMPLETION-CANDIDATES} (default: ${DEFAULT-VALUE}). " +
                    "'hybrid' subdivides the offending cell spatially (2x2 push-down) " +
                    "until each leaf fits one atlas page AND retains a low-resolution " +
                    "rescaled preview on each split cell root, replaced at runtime by " +
                    "the split leaves once they cross the LOD threshold — smoothest " +
                    "LOD cascade. 'split' runs the same subdivision but drops the preview: " +
                    "the split cell root becomes a content-less intermediate, the " +
                    "runtime refines straight from the parent aggregation to the " +
                    "split leaves (one fewer LOD level, faster export, sharper LOD " +
                    "transition). The few cells that cannot be subdivided further " +
                    "(single-feature / depth-cap residuals) are handled per " +
                    "--atlas-fallback in both split-based modes. 'flat' disables " +
                    "the split stage entirely: every overflowing cell is processed in " +
                    "place (no tree hierarchy introduced), with the outcome controlled by " +
                    "--atlas-fallback ('rescale' shrinks textures uniformly to fit " +
                    "--max-atlas-size; 'expand' grows the single I3S atlas up to 16K / " +
                    "spills onto multi-page 3D Tiles atlases).")
    private AtlasOverflowMode atlasOverflowMode;

    @CommandLine.Option(names = "--atlas-fallback", paramLabel = "<strategy>",
            defaultValue = "expand",
            description = "How to resolve texture overflow on cells the split " +
                    "stage could not (or did not) subdivide further — single-feature " +
                    "and depth-cap residuals under --atlas-overflow-mode=hybrid/split, " +
                    "or every overflowing cell under --atlas-overflow-mode=flat: " +
                    "${COMPLETION-CANDIDATES} (default: ${DEFAULT-VALUE}). 'expand' " +
                    "preserves source-resolution textures: 3D Tiles spills onto " +
                    "additional atlas pages (multi-page GLB), I3S grows the single " +
                    "atlas page up to the WebGL 16K cap. 'rescale' honors " +
                    "--max-atlas-size by shrinking textures uniformly, accepting " +
                    "silent quality loss; 3D Tiles forces single-atlas mode (no " +
                    "multi-page). Under --atlas-overflow-mode=hybrid the per-cell-root " +
                    "LOD preview always uses 'rescale' regardless of this flag, so " +
                    "the preview stays within --max-atlas-size.")
    private AtlasFallbackStrategy atlasFallbackStrategy;

    public double getTextureScale() {
        return textureScale;
    }

    public int getMaxAtlasSize() {
        return maxAtlasSize;
    }

    public AtlasOverflowMode getAtlasOverflowMode() {
        return atlasOverflowMode;
    }

    public AtlasFallbackStrategy getAtlasFallbackStrategy() {
        return atlasFallbackStrategy;
    }

    @Override
    public void preprocess(CommandLine commandLine) {
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
