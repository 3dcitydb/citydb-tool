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
    private boolean compressVertices;
    private boolean clampToGround;

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

    public boolean isCompressVertices() {
        return compressVertices;
    }

    public I3SFormatOptions setCompressVertices(boolean compressVertices) {
        this.compressVertices = compressVertices;
        return this;
    }

    public boolean isClampToGround() {
        return clampToGround;
    }

    public I3SFormatOptions setClampToGround(boolean clampToGround) {
        this.clampToGround = clampToGround;
        return this;
    }
}
