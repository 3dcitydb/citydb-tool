/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.model.geometry;

public sealed abstract class Surface<T extends Surface<?>> extends Geometry<T>
        permits Polygon, SurfaceCollection {
}
