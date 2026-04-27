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
    /**
     * Top-level strategy for handling cells whose textures don't fit a
     * single {@code maxAtlasSize}² atlas page.
     * <p>
     * {@link #QUADTREE} runs the spatial subdivision stage: the offending
     * cell is split 2×2 push-down until each leaf fits one atlas page or
     * cannot be subdivided further (single-feature / depth-cap residuals).
     * Residuals are handled per {@link AtlasFallbackStrategy}.
     * <p>
     * {@link #RESCALE} disables the split stage entirely: every overflowing
     * cell is processed in place, with textures shrunk uniformly to fit one
     * atlas page (legacy behavior, kept for debugging / size-strict
     * deployments). Implies {@link AtlasFallbackStrategy#RESCALE} regardless
     * of any explicit {@code --atlas-fallback} value — the two flags are
     * coupled here because "rescale mode" is a self-contained semantic
     * (no split + shrink to fit).
     */
    public enum AtlasOverflowMode {
        RESCALE, QUADTREE
    }

    /**
     * Strategy used when residual cells (single-feature or depth-cap fallback
     * after the quadtree split, or any cell when {@link AtlasOverflowMode}
     * {@code = RESCALE}) cannot fit their textures into the user's
     * {@code --max-atlas-size} budget.
     * <p>
     * {@link #RESCALE} shrinks textures uniformly via the iterative rescale
     * loop in {@code TextureAtlas.buildSingleAtlas} so the atlas page stays
     * within the user-requested cap, accepting silent quality loss. If the
     * rescale loop exhausts at its minimum scale and overflow remains, the
     * atlas page is allowed to grow as a last-resort fallback (mirroring
     * the {@link #EXPAND} mechanism); this is rare in practice. Per-format:
     * <ul>
     *   <li>3D Tiles uses {@link VisWriter.AtlasMode#SINGLE_ATLAS} (forces
     *       the rescale path, no multi-page fallback).</li>
     *   <li>I3S uses its mandatory single-atlas path; rescale is its
     *       primary defense before atlas-page expansion.</li>
     * </ul>
     * <p>
     * {@link #EXPAND} preserves source-resolution textures by skipping the
     * rescale loop entirely; the atlas page is allowed to grow beyond
     * {@code --max-atlas-size} (up to the 16K WebGL cap). Per-format:
     * <ul>
     *   <li>3D Tiles uses {@link VisWriter.AtlasMode#AUTO} so the GLB
     *       encoder spills overflow onto additional atlas pages
     *       ({@code TextureAtlas.buildMulti}) — preserves quality without
     *       inflating any single page.</li>
     *   <li>I3S (one-material-per-node spec) cannot multi-page, so it
     *       expands a single page up to 16K instead.</li>
     * </ul>
     */
    public enum AtlasFallbackStrategy {
        RESCALE, EXPAND
    }

    private double gridEdgeLength = 200.0;
    private double lodRefineRadius = 128.0;
    private boolean clampToGround;
    private double textureScale = 1.0;
    private int maxAtlasSize = 1024;
    private AtlasOverflowMode atlasOverflowMode = AtlasOverflowMode.QUADTREE;
    private AtlasFallbackStrategy atlasFallbackStrategy = AtlasFallbackStrategy.EXPAND;

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
        this.atlasOverflowMode = atlasOverflowMode != null ? atlasOverflowMode : AtlasOverflowMode.QUADTREE;
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
}
