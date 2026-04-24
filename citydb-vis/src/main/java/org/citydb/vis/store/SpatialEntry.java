/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences (HFT Stuttgart) <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.store;

/**
 * Compact spatial metadata for a processed feature.
 * <p>
 * During the write phase, instances are stored on disk via
 * {@link SpatialEntryStore} — all non-spatial data (objectId, featureType,
 * attributes) is stored in the {@link AttributeStore}. This reduces per-feature
 * heap usage to near zero, enabling 10M+ features without heap pressure.
 * <p>
 * During the close phase, entries are streamed back from disk for spatial
 * indexing (extent computation, grid partitioning, per-cell leaf build).
 */
public record SpatialEntry(long id, double centerX, double centerY, double[] bbox,
                           long meshHandle, long attrOffset) {
}
