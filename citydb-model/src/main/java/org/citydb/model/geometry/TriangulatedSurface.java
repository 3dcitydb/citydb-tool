/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.model.geometry;

import org.citydb.model.common.Visitor;
import org.citydb.model.util.CopySession;

import java.util.Collections;
import java.util.List;

public class TriangulatedSurface extends SurfaceCollection<TriangulatedSurface> {

    private TriangulatedSurface(int initialCapacity) {
        super(initialCapacity);
    }

    private TriangulatedSurface(List<Polygon> polygons) {
        super(polygons);
    }

    private TriangulatedSurface(Polygon... polygons) {
        super(polygons);
    }

    public static TriangulatedSurface of(List<Polygon> polygons) {
        return new TriangulatedSurface(polygons);
    }

    public static TriangulatedSurface of(Polygon... polygons) {
        return new TriangulatedSurface(polygons);
    }

    public static TriangulatedSurface empty() {
        return new TriangulatedSurface(Collections.emptyList());
    }

    @Override
    protected TriangulatedSurface createClone(CopySession session) {
        return new TriangulatedSurface(getPolygons().size());
    }

    @Override
    public GeometryType getGeometryType() {
        return GeometryType.TRIANGULATED_SURFACE;
    }

    @Override
    public void accept(Visitor visitor) {
        visitor.visit(this);
    }

    @Override
    TriangulatedSurface self() {
        return this;
    }
}
