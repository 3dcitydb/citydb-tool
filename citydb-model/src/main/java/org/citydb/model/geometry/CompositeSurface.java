/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.model.geometry;

import org.citydb.model.common.Visitor;

import java.util.Collections;
import java.util.List;

public class CompositeSurface extends SurfaceCollection<CompositeSurface> {

    private CompositeSurface(List<Polygon> polygons) {
        super(polygons);
    }

    private CompositeSurface(Polygon... polygons) {
        super(polygons);
    }

    public static CompositeSurface of(List<Polygon> polygons) {
        return new CompositeSurface(polygons);
    }

    public static CompositeSurface of(Polygon... polygons) {
        return new CompositeSurface(polygons);
    }

    public static CompositeSurface empty() {
        return new CompositeSurface(Collections.emptyList());
    }

    @Override
    public CompositeSurface copy() {
        return new CompositeSurface(getPolygons().stream()
                .map(Polygon::copy)
                .toArray(Polygon[]::new))
                .copyPropertiesFrom(this);
    }

    @Override
    public GeometryType getGeometryType() {
        return GeometryType.COMPOSITE_SURFACE;
    }

    @Override
    public void accept(Visitor visitor) {
        visitor.visit(this);
    }

    @Override
    CompositeSurface self() {
        return this;
    }
}
