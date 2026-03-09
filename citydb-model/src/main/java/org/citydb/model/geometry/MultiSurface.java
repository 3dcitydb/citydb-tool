/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.model.geometry;

import org.citydb.model.common.Visitor;

import java.util.Collections;
import java.util.List;

public class MultiSurface extends SurfaceCollection<MultiSurface> {

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
    public MultiSurface copy() {
        return new MultiSurface(getPolygons().stream()
                .map(Polygon::copy)
                .toArray(Polygon[]::new))
                .copyPropertiesFrom(this);
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
