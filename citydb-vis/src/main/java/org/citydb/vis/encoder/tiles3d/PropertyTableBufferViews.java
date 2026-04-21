/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.encoder.tiles3d;

/**
 * Buffer view ids for a single {@code EXT_structural_metadata} property.
 * <ul>
 *   <li>{@code valuesBv} — buffer view holding the packed property values</li>
 *   <li>{@code offsetsBv} — buffer view holding UTF-8 byte offsets for STRING
 *       properties, or {@code -1} for fixed-width numeric types</li>
 * </ul>
 */
record PropertyTableBufferViews(int valuesBv, int offsetsBv) {
}
