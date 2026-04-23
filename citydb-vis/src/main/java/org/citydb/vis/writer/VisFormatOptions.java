/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences (HFT Stuttgart) <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.writer;

import org.citydb.io.writer.options.OutputFormatOptions;

/**
 * Base format options shared by all visualization export formats (I3S, 3D Tiles, etc.).
 * <p>
 * Subclasses add format-specific options (e.g., I3S node page constants,
 * 3D Tiles geometric error strategy) while inheriting the common spatial
 * partitioning and texture handling parameters.
 */
public abstract class VisFormatOptions implements OutputFormatOptions {
    private int maxFeaturesPerNode = 64;
    private boolean clampToGround;
    private double textureScale = 1.0;
    private int maxAtlasSize = 2048;

    public int getMaxFeaturesPerNode() {
        return maxFeaturesPerNode;
    }

    public VisFormatOptions setMaxFeaturesPerNode(int maxFeaturesPerNode) {
        this.maxFeaturesPerNode = maxFeaturesPerNode;
        return this;
    }

    public boolean isClampToGround() {
        return clampToGround;
    }

    public VisFormatOptions setClampToGround(boolean clampToGround) {
        this.clampToGround = clampToGround;
        return this;
    }

    public double getTextureScale() {
        return textureScale;
    }

    public VisFormatOptions setTextureScale(double textureScale) {
        this.textureScale = Math.max(0.01, Math.min(1.0, textureScale));
        return this;
    }

    public int getMaxAtlasSize() {
        return maxAtlasSize;
    }

    public VisFormatOptions setMaxAtlasSize(int maxAtlasSize) {
        this.maxAtlasSize = Math.max(1024, Math.min(16384, maxAtlasSize));
        return this;
    }
}
