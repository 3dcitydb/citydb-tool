/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.model.geometry;

import org.citydb.model.common.Visitor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class LineString extends Geometry<LineString> {
    private final List<Coordinate> points;

    private LineString(List<Coordinate> points) {
        this.points = Objects.requireNonNull(points, "The point list must not be null.");
    }

    private LineString(Coordinate[] points) {
        Objects.requireNonNull(points, "The point array must not be null.");
        this.points = new ArrayList<>(Arrays.asList(points));
    }

    public static LineString of(List<Coordinate> points) {
        return new LineString(points);
    }

    public static LineString of(Coordinate[] points) {
        return new LineString(points);
    }

    public static LineString of(List<Double> coordinates, int dimension) {
        return new LineString(Coordinate.of(coordinates, dimension));
    }

    public static LineString empty() {
        return new LineString(new ArrayList<>());
    }

    public List<Coordinate> getPoints() {
        return points;
    }

    @Override
    public int getVertexDimension() {
        return points.stream().anyMatch(coordinate -> coordinate.getDimension() == 2) ? 2 : 3;
    }

    @Override
    public LineString force2D() {
        points.forEach(Coordinate::force2D);
        return this;
    }

    @Override
    public LineString copy() {
        return new LineString(points.stream()
                .map(Coordinate::copy)
                .toArray(Coordinate[]::new))
                .copyPropertiesFrom(this);
    }

    @Override
    public GeometryType getGeometryType() {
        return GeometryType.LINE_STRING;
    }

    @Override
    public void accept(Visitor visitor) {
        visitor.visit(this);
    }

    @Override
    LineString self() {
        return this;
    }
}
