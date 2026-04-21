/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences (HFT Stuttgart) <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.model.tiles3d;

public record CellReference(TileBoundingVolume boundingVolume,
                             double geometricError, String uri) {
}
