/*
 * citydb-tool - Command-line tool for the 3D City Database
 * https://www.3dcitydb.org/
 *
 * Copyright 2022-2023
 * virtualcitysystems GmbH, Germany
 * https://vc.systems/
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
