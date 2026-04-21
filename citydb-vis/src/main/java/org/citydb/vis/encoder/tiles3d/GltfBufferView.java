/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.encoder.tiles3d;

/**
 * Offset and length of a buffer view within a GLB BIN chunk.
 */
public record GltfBufferView(int byteOffset, int byteLength) {
}
