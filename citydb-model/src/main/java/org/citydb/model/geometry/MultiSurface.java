/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.model.geometry;

import org.citydb.model.common.Visitor;
import org.citydb.model.util.CopySession;

import java.util.Collections;
import java.util.List;

public class MultiSurface extends SurfaceCollection<MultiSurface> {

    private MultiSurface(int initialCapacity) {
        super(initialCapacity);
    }

    private MultiSurface(List<Polygon> polygons) {
        super(polygons);
    }

    private MultiSurface(Polygon... polygons) {
        super(polygons);
    }

    public static MultiSurface of(List<Polygon> polygons) {
        return new MultiSurface(polygons);
    }

    public static MultiSurface of(Polygon... polygons) {
        return new MultiSurface(polygons);
    }

    public static MultiSurface empty() {
        return new MultiSurface(Collections.emptyList());
    }

    @Override
    protected MultiSurface createClone(CopySession session) {
        return new MultiSurface(getPolygons().size());
    }

    @Override
    public GeometryType getGeometryType() {
        return GeometryType.MULTI_SURFACE;
    }

    @Override
    public void accept(Visitor visitor) {
        visitor.visit(this);
    }

    @Override
    MultiSurface self() {
        return this;
    }
}
