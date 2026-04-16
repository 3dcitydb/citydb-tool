/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.vis.encoder.tiles3d;

/**
 * Offset and length of a buffer view within a GLB BIN chunk.
 */
public record GltfBufferView(int byteOffset, int byteLength) {
}
