/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.encoder.tiles3d;

/**
 * Buffer view ids for a single {@code EXT_structural_metadata} property.
 * <ul>
 *   <li>{@code valuesBv} — buffer view holding the packed property values, or
 *       {@code -1} when the column is skipped (see {@link #SKIPPED})</li>
 *   <li>{@code offsetsBv} — buffer view holding UTF-8 byte offsets for STRING
 *       properties, or {@code -1} for fixed-width numeric types and skipped
 *       columns</li>
 * </ul>
 */
record PropertyTableBufferViews(int valuesBv, int offsetsBv) {
    /**
     * Sentinel for STRING columns where every value is empty. glTF forbids
     * a 0-byte bufferView and EXT_structural_metadata forbids any other
     * byteLength when the offsets total 0, so the only legal output is to
     * omit the property from the table.
     */
    static final PropertyTableBufferViews SKIPPED = new PropertyTableBufferViews(-1, -1);
}
