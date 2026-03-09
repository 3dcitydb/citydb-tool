/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.model.geometry;

import org.citydb.model.common.Visitor;

import java.util.Collections;
import java.util.List;

public class TriangulatedSurface extends SurfaceCollection<TriangulatedSurface> {

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
    public TriangulatedSurface copy() {
        return new TriangulatedSurface(getPolygons().stream()
                .map(Polygon::copy)
                .toArray(Polygon[]::new))
                .copyPropertiesFrom(this);
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
