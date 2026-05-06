/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences (HFT Stuttgart) <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.config;

import org.citydb.io.writer.options.OutputFormatOptions;
import org.citydb.vis.appearance.AtlasFallbackStrategy;
import org.citydb.vis.appearance.AtlasOverflowMode;
import org.citydb.vis.styling.DefaultObjectStyle;
import org.citydb.vis.styling.ObjectStyleRegistry;

/**
 * Base format options shared by all visualization export formats (I3S, 3D Tiles, etc.).
 * <p>
 * Subclasses add format-specific options (e.g., I3S node page constants,
 * 3D Tiles geometric error strategy) while inheriting the common spatial
 * partitioning and texture handling parameters.
 */
public abstract class VisFormatOptions implements OutputFormatOptions {
    // 0 is a sentinel for "auto": PartitioningStage treats it as a single
    // root cell sized to the dataset extent's longest side (no spatial
    // subdivision). Override with a positive value for a finer grid.
    private double gridEdgeLength = 0.0;
    // Default: refine when projected MBS radius exceeds 56 px — safe for
    // city-scale data. 0 is a sentinel both writers translate into an
    // always-refine policy (every leaf loads immediately); useful for
    // small exports / debugging but easily crashes the viewer on large
    // datasets, so users must opt in explicitly with --lod-refine-radius=0.
    private double lodRefineRadius = 56.0;
    private boolean clampToGround;
    private double textureScale = 1.0;
    private int maxAtlasSize = 1024;
    private AtlasOverflowMode atlasOverflowMode = AtlasOverflowMode.HYBRID;
    private AtlasFallbackStrategy atlasFallbackStrategy = AtlasFallbackStrategy.EXPAND;
    private boolean enableShading;
    private ObjectStyleRegistry styleRegistry = ObjectStyleRegistry.empty();

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

    /**
     * When {@code true}, every primitive carries a NORMAL attribute and
     * renders PBR-shaded — both 3D Tiles GLB and I3S behave identically.
     * Plain, X3DMaterial-colored, and per-feature-type-styled paths use
     * the polygon's real geometric normal so Lambertian darkening provides
     * 3D form. Textured paths use the local "up" direction instead of the
     * geometric normal so walls and roofs in the same node end up equally
     * lit (no per-face dimming on back-facing walls), while still
     * responding to time-of-day sun changes as a group. On I3S, white-pixel
     * sentinel triangles in mixed-feature textured nodes keep their real
     * geometric normal so they pick up proper PBR shading.
     * <p>
     * When {@code false} (default), NORMAL is dropped on every path and
     * primitives render unlit ({@code KHR_materials_unlit} on the GLB
     * side; NORMAL absence on the I3S side).
     */
    public boolean isEnableShading() {
        return enableShading;
    }

    public VisFormatOptions setEnableShading(boolean enableShading) {
        this.enableShading = enableShading;
        return this;
    }

    /**
     * Global fallback style for surfaces without an explicit per-feature-type
     * override. Mirrored from {@link #getStyleRegistry()} so I3S — which
     * cannot consume the per-type registry due to its node-level material
     * model — keeps its current behavior of applying one style to every
     * no-appearance surface in a scene layer.
     */
    public DefaultObjectStyle getDefaultObjectStyle() {
        return styleRegistry.defaultStyle();
    }

    public ObjectStyleRegistry getStyleRegistry() {
        return styleRegistry;
    }

    public VisFormatOptions setStyleRegistry(ObjectStyleRegistry styleRegistry) {
        this.styleRegistry = styleRegistry != null ? styleRegistry : ObjectStyleRegistry.empty();
        return this;
    }
}
