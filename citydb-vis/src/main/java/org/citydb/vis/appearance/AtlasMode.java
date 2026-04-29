/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences (HFT Stuttgart) <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.appearance;

/**
 * Strategy for atlas page generation per node. Selected by each writer
 * subclass and passed to {@code VisWriter.prepareNodeMesh} as a hint to
 * pick between the single-page and multi-page atlas builders. Distinct
 * from {@link AtlasFallbackStrategy} (which controls how a single page
 * resolves overflow) — the two compose: a writer first decides whether
 * to emit one page or many ({@code AtlasMode}), and within the
 * single-page path the fallback strategy decides whether to rescale or
 * expand the page.
 */
public enum AtlasMode {
    /**
     * Force a single atlas page. Required for I3S because the spec
     * permits only one material per node; overflow is handled by the
     * single-atlas path's rescale + atlas-size expansion fallback.
     */
    SINGLE_ATLAS,
    /**
     * Single page when the textures fit; spill onto additional pages
     * only when the BSP packer would otherwise overflow even after
     * per-texture clamping. Preserves source resolution on the
     * residual cells that the
     * {@link org.citydb.vis.pipeline.stages.AtlasOverflowQuadtreeStage}
     * could not subdivide further (single-feature or depth-cap
     * fallback). Used by 3D Tiles, whose GLB supports multiple
     * primitives per mesh.
     */
    AUTO
}
