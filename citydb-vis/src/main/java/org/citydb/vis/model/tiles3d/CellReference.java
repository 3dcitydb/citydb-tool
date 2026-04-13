/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.vis.model.tiles3d;

public record CellReference(TileBoundingVolume boundingVolume,
                             double geometricError, String uri) {
}
