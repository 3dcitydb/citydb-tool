/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.geometry;

import org.citydb.model.appearance.TextureCoordinate;
import org.citydb.model.geometry.LinearRing;

import java.util.List;
import java.util.Map;

/**
 * Per-ring appearance lookups consumed by triangulation: UV coordinates,
 * atlas texture id, and X3D-material RGBA, each keyed by {@link LinearRing}
 * identity. Any map is {@code null} (not empty) when the feature carries no
 * data of that kind, so consumers branch with a single null check.
 * <p>
 * This is the geometry-package counterpart of
 * {@code org.citydb.vis.appearance.RingAppearance}: the two records hold the
 * same data, but the triangulation code lives in {@code vis.geometry}, which
 * must not depend on {@code vis.appearance} (that package already depends on
 * geometry via {@code TextureAtlas} → {@code TriangleMesh}, so the reverse
 * edge would create a package cycle). {@code RingAppearance.forTriangulation()}
 * bridges across the boundary. Only model-package types appear here.
 */
public record RingAttributes(Map<LinearRing, List<TextureCoordinate>> texCoords,
                             Map<LinearRing, Integer> ringTextureIds,
                             Map<LinearRing, float[]> ringColors) {
}
