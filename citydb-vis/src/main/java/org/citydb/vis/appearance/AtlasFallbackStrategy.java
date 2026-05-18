/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences (HFT Stuttgart) <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.appearance;

/**
 * Strategy used when residual cells (single-feature or depth-cap fallback
 * after the spatial split, or any cell when {@link AtlasOverflowMode}
 * {@code = FLAT}) cannot fit their textures into the user's
 * {@code --max-atlas-size} budget.
 * <p>
 * {@link #RESCALE} shrinks textures uniformly via the iterative rescale
 * loop in {@code TextureAtlas.buildSingleAtlas} so the atlas page stays
 * within the user-requested cap, accepting silent quality loss. If the
 * rescale loop exhausts at its minimum scale and overflow remains, the
 * atlas page is allowed to grow as a last-resort fallback (mirroring
 * the {@link #EXPAND} mechanism); this is rare in practice. Per-format:
 * <ul>
 *   <li>3D Tiles uses {@link AtlasMode#SINGLE_ATLAS} (forces
 *       the rescale path, no multi-page fallback).</li>
 *   <li>I3S uses its mandatory single-atlas path; rescale is its
 *       primary defense before atlas-page expansion.</li>
 * </ul>
 * <p>
 * {@link #EXPAND} preserves source-resolution textures by skipping the
 * rescale loop entirely; the atlas page is allowed to grow beyond
 * {@code --max-atlas-size} (up to the 16K WebGL cap). Per-format:
 * <ul>
 *   <li>3D Tiles uses {@link AtlasMode#AUTO} so the GLB
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
