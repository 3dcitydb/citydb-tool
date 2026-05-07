/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.encoder.tiles3d;

/**
 * Offset, length, and optional usage target of a buffer view within a GLB BIN
 * chunk. {@code target} is {@code 34962} (ARRAY_BUFFER) for vertex attribute
 * data, {@code 34963} (ELEMENT_ARRAY_BUFFER) for index data, or {@code 0}
 * (omit) for non-geometry payloads (images, property tables).
 */
public record GltfBufferView(int byteOffset, int byteLength, int target) {
    public static final int TARGET_NONE = 0;
    public static final int TARGET_ARRAY_BUFFER = 34962;
    public static final int TARGET_ELEMENT_ARRAY_BUFFER = 34963;
}
