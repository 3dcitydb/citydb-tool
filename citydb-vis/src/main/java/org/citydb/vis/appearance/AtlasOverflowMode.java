/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences (HFT Stuttgart) <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.appearance;

/**
 * Top-level strategy for handling cells whose textures don't fit a
 * single {@code maxAtlasSize}² atlas page.
 * <p>
 * {@link #QUADTREE} runs the spatial subdivision stage in pure-split
 * mode: the offending cell is split 2×2 push-down until each leaf
 * fits one atlas page or cannot be subdivided further (single-feature
 * / depth-cap residuals). The original cell root becomes a
 * content-less intermediate — the runtime refines from the cell's
 * parent aggregation directly to the quadtree leaves. Residuals are
 * handled per {@link AtlasFallbackStrategy}.
 * <p>
 * {@link #HYBRID} combines the two strategies: runs the same quadtree
 * subdivision as {@link #QUADTREE} and additionally retains a
 * low-resolution rescaled (RESCALE) preview on each split cell root,
 * replaced at runtime by the quadtree-leaf children once they cross
 * the LOD threshold. Trades a small extra encode cost (one rescaled
 * atlas per split root) for a smoother LOD cascade in viewers that
 * have to wait for leaf data to load. The cell-root preview always
 * uses {@link AtlasFallbackStrategy#RESCALE} regardless of
 * {@code --atlas-fallback}, so it stays within {@code --max-atlas-size}.
 * <p>
 * {@link #FLAT} disables the split stage entirely: every overflowing
 * cell is processed in place (no tree hierarchy introduced), with the
 * outcome controlled by the user's {@link AtlasFallbackStrategy}.
 * {@code rescale} shrinks textures uniformly to fit a single
 * {@code --max-atlas-size} page (legacy behavior, size-strict
 * deployments); {@code expand} preserves source-resolution textures —
 * I3S grows its single atlas up to the 16K cap, 3D Tiles spills onto
 * multi-page atlases — at the cost of larger node payloads.
 */
public enum AtlasOverflowMode {
    FLAT, QUADTREE, HYBRID
}
