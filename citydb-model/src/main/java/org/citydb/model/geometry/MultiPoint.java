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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class MultiPoint extends Geometry<MultiPoint> {
    private final List<Point> points;

    private MultiPoint(List<Point> points) {
        Objects.requireNonNull(points, "The point list must not be null.");
        this.points = asChild(points);
    }

    private MultiPoint(Point[] points) {
        Objects.requireNonNull(points, "The point array must not be null.");
        this.points = asChild(Arrays.asList(points));
    }

    public static MultiPoint of(List<Point> points) {
        return new MultiPoint(points);
    }

    public static MultiPoint of(Point[] points) {
        return new MultiPoint(points);
    }

    public static MultiPoint empty() {
        return new MultiPoint(Collections.emptyList());
    }

    public List<Point> getPoints() {
        return points;
    }

    @Override
    public int getVertexDimension() {
        return points.stream().anyMatch(point -> point.getVertexDimension() == 2) ? 2 : 3;
    }

    @Override
    public MultiPoint force2D() {
        points.forEach(Point::force2D);
        return this;
    }

    @Override
    public GeometryType getGeometryType() {
        return GeometryType.MULTI_POINT;
    }

    @Override
    public void accept(Visitor visitor) {
        visitor.visit(this);
    }

    @Override
    MultiPoint self() {
        return this;
    }
}
