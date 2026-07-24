/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.model.geometry;

import org.citydb.model.common.Child;
import org.citydb.model.common.ChildList;
import org.citydb.model.common.Visitor;
import org.citydb.model.util.CopySession;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class MultiPoint extends Geometry<MultiPoint> {
    private final List<Point> points;

    private MultiPoint(int initialCapacity) {
        points = new ChildList<>(initialCapacity, this);
    }

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
    protected MultiPoint createClone(CopySession session) {
        return new MultiPoint(points.size());
    }

    @Override
    protected void copyPropertiesTo(Child clone, CopySession session) {
        MultiPoint multiPoint = (MultiPoint) clone;
        super.copyPropertiesTo(multiPoint, session);

        for (Point point : points) {
            multiPoint.points.add(copy(point, session));
        }
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
