/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.vis.writer;

/**
 * Compact spatial metadata for a processed feature.
 * <p>
 * Kept in memory during the write phase — all non-spatial data (objectId,
 * featureType, attributes) is stored in the {@link AttributeStore} on disk.
 * This reduces per-feature heap usage from ~300 bytes (FeatureData with
 * strings and Map) to ~84 bytes, enabling 10M+ features without heap overflow.
 *
 * @param textureId global texture ID from {@link TextureStore}, or -1 if untextured
 */
record SpatialEntry(long id, double centerX, double centerY, double[] bbox,
                    long meshHandle, long attrOffset, int textureId) {
}
