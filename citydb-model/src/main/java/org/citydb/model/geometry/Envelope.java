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

import org.citydb.model.common.Child;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

public class Envelope extends Child implements SpatialObject {
    private final Coordinate lowerCorner;
    private final Coordinate upperCorner;
    private Integer srid;
    private String srsIdentifier;

    private Envelope(Coordinate lowerCorner, Coordinate upperCorner) {
        this.lowerCorner = Objects.requireNonNull(lowerCorner, "The lower corner must not be null.");
        this.upperCorner = Objects.requireNonNull(upperCorner, "The upper corner must not be null.");
    }

    public static Envelope of(Coordinate lowerCorner, Coordinate upperCorner) {
        return new Envelope(lowerCorner, upperCorner);
    }

    public static Envelope empty() {
        return new Envelope(
                Coordinate.of(Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE),
                Coordinate.of(-Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE));
    }

    public Coordinate getLowerCorner() {
        return lowerCorner;
    }

    public Coordinate getUpperCorner() {
        return upperCorner;
    }

    @Override
    public int getVertexDimension() {
        return lowerCorner.getDimension() == 2 || upperCorner.getDimension() == 2 ? 2 : 3;
    }

    @Override
    public Optional<Integer> getSRID() {
        if (srid == null) {
            SrsReference parent = getInheritedSrsReference();
            if (parent != null) {
                return parent.getSRID();
            }
        }

        return Optional.ofNullable(srid);
    }

    @Override
    public Envelope setSRID(Integer srid) {
        this.srid = srid;
        return this;
    }

    @Override
    public Optional<String> getSrsIdentifier() {
        if (srsIdentifier == null) {
            SrsReference parent = getInheritedSrsReference();
            if (parent != null) {
                return parent.getSrsIdentifier();
            }
        }

        return Optional.ofNullable(srsIdentifier);
    }

    @Override
    public Envelope setSrsIdentifier(String srsIdentifier) {
        this.srsIdentifier = srsIdentifier;
        return this;
    }

    public Envelope force2D() {
        lowerCorner.force2D();
        upperCorner.force2D();
        return this;
    }

    public Coordinate getCenter() {
        Coordinate center = Coordinate.of(
                (lowerCorner.getX() + upperCorner.getX()) / 2,
                (lowerCorner.getY() + upperCorner.getY()) / 2,
                (lowerCorner.getZ() + upperCorner.getZ()) / 2);

        return getVertexDimension() == 2 ? center.force2D() : center;
    }

    public boolean contains(Coordinate coordinate) {
        boolean contains = coordinate.getX() >= lowerCorner.getX()
                && coordinate.getX() <= upperCorner.getX()
                && coordinate.getY() >= lowerCorner.getY()
                && coordinate.getY() <= upperCorner.getY();

        if (contains && coordinate.getDimension() == 3 && getVertexDimension() == 3) {
            contains = coordinate.getZ() >= lowerCorner.getZ()
                    && coordinate.getZ() <= upperCorner.getZ();
        }

        return contains;
    }

    public boolean contains(Envelope envelope) {
        return contains(envelope.lowerCorner) && contains(envelope.upperCorner);
    }

    public boolean within(Coordinate coordinate) {
        boolean within = coordinate.getX() > lowerCorner.getX()
                && coordinate.getX() < upperCorner.getX()
                && coordinate.getY() > lowerCorner.getY()
                && coordinate.getY() < upperCorner.getY();

        if (within && coordinate.getDimension() == 3 && getVertexDimension() == 3) {
            within = coordinate.getZ() > lowerCorner.getZ()
                    && coordinate.getZ() < upperCorner.getZ();
        }

        return within;
    }

    public boolean within(Envelope envelope) {
        return within(envelope.lowerCorner) && within(envelope.upperCorner);
    }

    public boolean intersects(Envelope envelope) {
        boolean disconnected = envelope.lowerCorner.getX() > upperCorner.getX()
                || envelope.upperCorner.getX() < lowerCorner.getX()
                || envelope.lowerCorner.getY() > upperCorner.getY()
                || envelope.upperCorner.getY() < lowerCorner.getY();

        if (!disconnected && envelope.getVertexDimension() == 3 && getVertexDimension() == 3) {
            disconnected = envelope.lowerCorner.getZ() > upperCorner.getZ()
                    || envelope.upperCorner.getZ() < lowerCorner.getZ();
        }

        return !disconnected;
    }

    public boolean isOnTile(Envelope envelope) {
        return isOnTile(envelope.getCenter());
    }

    public boolean isOnTile(Coordinate point) {
        return point.getX() > lowerCorner.getX()
                && point.getX() <= upperCorner.getX()
                && point.getY() > lowerCorner.getY()
                && point.getY() <= upperCorner.getY();
    }

    public Envelope include(double x, double y) {
        if (x < lowerCorner.getX()) {
            lowerCorner.setX(x);
        }

        if (y < lowerCorner.getY()) {
            lowerCorner.setY(y);
        }

        if (x > upperCorner.getX()) {
            upperCorner.setX(x);
        }

        if (y > upperCorner.getY()) {
            upperCorner.setY(y);
        }

        return this;
    }

    public Envelope include(double x, double y, double z) {
        include(x, y);

        if (z < lowerCorner.getZ()) {
            lowerCorner.setZ(z);
        }

        if (z > upperCorner.getZ()) {
            upperCorner.setZ(z);
        }

        return this;
    }

    public Envelope include(Coordinate coordinate) {
        return coordinate.getDimension() == 2 ?
                include(coordinate.getX(), coordinate.getY()) :
                include(coordinate.getX(), coordinate.getY(), coordinate.getZ());
    }

    public Envelope include(Envelope envelope) {
        return include(envelope.lowerCorner)
                .include(envelope.upperCorner);
    }

    public Envelope include(Geometry<?> geometry) {
        return include(geometry.getEnvelope());
    }

    public boolean isEmpty() {
        return lowerCorner.getX() == Double.MAX_VALUE
                && lowerCorner.getY() == Double.MAX_VALUE
                && lowerCorner.getZ() == Double.MAX_VALUE
                && upperCorner.getX() == -Double.MAX_VALUE
                && upperCorner.getY() == -Double.MAX_VALUE
                && upperCorner.getZ() == -Double.MAX_VALUE;
    }

    public Polygon convertToPolygon() {
        Coordinate lowerRight = Coordinate.of(upperCorner.getX(), lowerCorner.getY());
        Coordinate upperLeft = Coordinate.of(lowerCorner.getX(), upperCorner.getY());
        if (getVertexDimension() == 3) {
            lowerRight.setZ(lowerCorner.getZ());
            upperLeft.setZ(upperCorner.getZ());
        }

        return Polygon.of(LinearRing.of(Arrays.asList(lowerCorner, lowerRight, upperCorner, upperLeft, lowerCorner)))
                .setSRID(srid)
                .setSrsIdentifier(srsIdentifier);
    }
}
