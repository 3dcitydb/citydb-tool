/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.model.geometry;

import org.citydb.model.common.Child;
import org.citydb.model.common.Referencable;

import java.util.*;

public class LinearRing extends Child implements Referencable {
    private String objectId;
    private final List<Coordinate> points;

    private LinearRing(List<Coordinate> points) {
        this.points = Objects.requireNonNull(points, "The point list must not be null.");
    }

    private LinearRing(Coordinate[] points) {
        Objects.requireNonNull(points, "The point array must not be null.");
        this.points = new ArrayList<>(Arrays.asList(points));
    }

    public static LinearRing of(List<Coordinate> points) {
        return new LinearRing(points);
    }

    public static LinearRing of(Coordinate[] points) {
        return new LinearRing(points);
    }

    public static LinearRing of(List<Double> coordinates, int dimension) {
        return new LinearRing(Coordinate.of(coordinates, dimension));
    }

    public static LinearRing empty() {
        return new LinearRing(new ArrayList<>());
    }

    @Override
    public Optional<String> getObjectId() {
        return Optional.ofNullable(objectId);
    }

    @Override
    public LinearRing setObjectId(String objectId) {
        this.objectId = objectId;
        return this;
    }

    public List<Coordinate> getPoints() {
        return points;
    }

    public int getVertexDimension() {
        return points.stream().anyMatch(coordinate -> coordinate.getDimension() == 2) ? 2 : 3;
    }

    public LinearRing force2D() {
        points.forEach(Coordinate::force2D);
        return this;
    }

    public LinearRing copy() {
        return new LinearRing(points.stream()
                .map(Coordinate::copy)
                .toArray(Coordinate[]::new))
                .setObjectId(objectId);
    }
}
