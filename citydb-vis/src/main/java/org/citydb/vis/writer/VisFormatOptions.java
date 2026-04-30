/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences (HFT Stuttgart) <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.writer;

import org.citydb.io.writer.options.OutputFormatOptions;
import org.citydb.vis.appearance.AtlasFallbackStrategy;
import org.citydb.vis.appearance.AtlasOverflowMode;
import org.citydb.vis.styling.DefaultObjectStyle;

/**
 * Base format options shared by all visualization export formats (I3S, 3D Tiles, etc.).
 * <p>
 * Subclasses add format-specific options (e.g., I3S node page constants,
 * 3D Tiles geometric error strategy) while inheriting the common spatial
 * partitioning and texture handling parameters.
 */
public abstract class VisFormatOptions implements OutputFormatOptions {
    private double gridEdgeLength = 200.0;
    private double lodRefineRadius = 128.0;
    private boolean clampToGround;
    private double textureScale = 1.0;
    private int maxAtlasSize = 1024;
    private AtlasOverflowMode atlasOverflowMode = AtlasOverflowMode.HYBRID;
    private AtlasFallbackStrategy atlasFallbackStrategy = AtlasFallbackStrategy.EXPAND;
    private DefaultObjectStyle defaultObjectStyle = DefaultObjectStyle.defaults();

    public double getGridEdgeLength() {
        return gridEdgeLength;
    }

    public VisFormatOptions setGridEdgeLength(double gridEdgeLength) {
        this.gridEdgeLength = gridEdgeLength;
        return this;
    }

    public double getLodRefineRadius() {
        return lodRefineRadius;
    }

    public VisFormatOptions setLodRefineRadius(double lodRefineRadius) {
        this.lodRefineRadius = lodRefineRadius;
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

    public AtlasOverflowMode getAtlasOverflowMode() {
        return atlasOverflowMode;
    }

    public VisFormatOptions setAtlasOverflowMode(AtlasOverflowMode atlasOverflowMode) {
        this.atlasOverflowMode = atlasOverflowMode != null ? atlasOverflowMode : AtlasOverflowMode.HYBRID;
        return this;
    }

    public AtlasFallbackStrategy getAtlasFallbackStrategy() {
        // When the quadtree stage is disabled (--atlas-overflow-mode=rescale)
        // the user has chosen the "rescale everything" mode: no spatial split
        // and shrink textures uniformly when an unsplit cell's atlas would
        // overflow. Force the fallback to RESCALE here so downstream logic
        // (Tiles3DWriter's AtlasMode selection, TextureAtlas.build Phase 1)
        // sees a single coherent strategy without needing to consult both
        // flags. The stored atlasFallbackStrategy is preserved verbatim;
        // only the effective value seen at runtime is overridden.
        if (atlasOverflowMode == AtlasOverflowMode.RESCALE) {
            return AtlasFallbackStrategy.RESCALE;
        }
        return atlasFallbackStrategy;
    }

    public VisFormatOptions setAtlasFallbackStrategy(AtlasFallbackStrategy atlasFallbackStrategy) {
        this.atlasFallbackStrategy = atlasFallbackStrategy != null ? atlasFallbackStrategy : AtlasFallbackStrategy.EXPAND;
        return this;
    }

    public DefaultObjectStyle getDefaultObjectStyle() {
        return defaultObjectStyle;
    }

    public VisFormatOptions setDefaultObjectStyle(DefaultObjectStyle defaultObjectStyle) {
        this.defaultObjectStyle = defaultObjectStyle != null ? defaultObjectStyle : DefaultObjectStyle.defaults();
        return this;
    }
}
