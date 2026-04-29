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
 * have to wait for leaf data to load.
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
    RESCALE, QUADTREE, HYBRID
}
