/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.encoder.tiles3d;

import java.util.List;
import java.util.Map;

/**
 * Serialized atlas pages of one instanced prototype, ready for GLB embedding:
 * the per-page JPEG bytes plus the texture-id → page-index map the encoder
 * uses to route the prototype's textured triangles to the primitive backed by
 * the correct page. Built by the 3D Tiles writer from the prototype's
 * {@code TextureAtlas} pages (JPEG bytes canonicalized per page via the
 * writer's cache, so identical byte arrays recur across nodes and the
 * encoder's per-prototype geometry cache stays valid).
 * <p>
 * Usually a single page; multiple pages when the prototype's textures
 * overflow {@code --max-atlas-size} under {@code --atlas-fallback=expand} —
 * mirroring the node atlas's multi-page spill, so prototype atlases obey the
 * same page-size contract as node atlases.
 *
 * @param pageBytes   JPEG bytes per atlas page
 * @param texIdToPage texture id → index into {@code pageBytes}
 */
public record InstancedAtlas(List<byte[]> pageBytes, Map<Integer, Integer> texIdToPage) {
}
