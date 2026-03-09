/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.model.geometry;

import org.citydb.model.common.Visitor;

import java.util.Objects;

public class Point extends Geometry<Point> {
    private final Coordinate coordinate;

    private Point(Coordinate coordinate) {
        this.coordinate = Objects.requireNonNull(coordinate, "The point coordinate must not be null.");
    }

    public static Point of(Coordinate coordinate) {
        return new Point(coordinate);
    }

    public Coordinate getCoordinate() {
        return coordinate;
    }

    @Override
    public int getVertexDimension() {
        return coordinate.getDimension();
    }

    @Override
    public Point force2D() {
        coordinate.force2D();
        return this;
    }

    @Override
    public Point copy() {
        return new Point(coordinate.copy())
                .copyPropertiesFrom(this);
    }

    @Override
    public GeometryType getGeometryType() {
        return GeometryType.POINT;
    }

    @Override
    public void accept(Visitor visitor) {
        visitor.visit(this);
    }

    @Override
    Point self() {
        return this;
    }
}
