/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.vis;

import org.citydb.config.SerializableConfig;
import org.citydb.io.writer.options.OutputFormatOptions;

@SerializableConfig(name = "I3S")
public class I3SFormatOptions implements OutputFormatOptions {
    private int maxFeaturesPerNode = 64;
    private int maxTreeDepth = 16;
    private boolean clampToGround;
    private double textureScale = 1.0;
    private int maxAtlasSize = 2048;

    public int getMaxFeaturesPerNode() {
        return maxFeaturesPerNode;
    }

    public I3SFormatOptions setMaxFeaturesPerNode(int maxFeaturesPerNode) {
        this.maxFeaturesPerNode = maxFeaturesPerNode;
        return this;
    }

    public int getMaxTreeDepth() {
        return maxTreeDepth;
    }

    public I3SFormatOptions setMaxTreeDepth(int maxTreeDepth) {
        this.maxTreeDepth = maxTreeDepth;
        return this;
    }

    public boolean isClampToGround() {
        return clampToGround;
    }

    public I3SFormatOptions setClampToGround(boolean clampToGround) {
        this.clampToGround = clampToGround;
        return this;
    }

    public double getTextureScale() {
        return textureScale;
    }

    public I3SFormatOptions setTextureScale(double textureScale) {
        this.textureScale = Math.max(0.01, Math.min(1.0, textureScale));
        return this;
    }

    public int getMaxAtlasSize() {
        return maxAtlasSize;
    }

    public I3SFormatOptions setMaxAtlasSize(int maxAtlasSize) {
        this.maxAtlasSize = Math.max(1024, Math.min(16384, maxAtlasSize));
        return this;
    }
}
