/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.model.geometry;

public interface SpatialObject extends SrsReference {
    int getVertexDimension();
}
