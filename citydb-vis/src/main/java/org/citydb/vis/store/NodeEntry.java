/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences (HFT Stuttgart) <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.store;

/**
 * Compact post-quadtree entry holding only the fields needed by the output
 * phase: feature ID, mesh handle, and attribute offset. At 24 bytes on disk
 * (vs 88 for {@link SpatialEntry}), this reduces heap and disk pressure by
 * ~3x once spatial fields are no longer needed.
 */
public record NodeEntry(long id, long meshHandle, long attrOffset) {
}
