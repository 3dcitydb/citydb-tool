/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.model.geometry;

public sealed interface SpatialObject extends SrsReference
        permits Envelope, Geometry, GeometryCollection {
    int getVertexDimension();
}
