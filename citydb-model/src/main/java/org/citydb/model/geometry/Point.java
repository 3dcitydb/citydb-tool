/*
 * citydb-tool - Command-line tool for the 3D City Database
 * https://www.3dcitydb.org/
 *
 * Copyright 2022-2024
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
