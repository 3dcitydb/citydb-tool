/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.model.geometry;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class Coordinate implements Serializable {
    private double x;
    private double y;
    private double z;
    private int dimension;

    private Coordinate(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
        dimension = 3;
    }

    private Coordinate(double x, double y) {
        this.x = x;
        this.y = y;
        z = 0;
        dimension = 2;
    }

    public static Coordinate of(double x, double y, double z) {
        return new Coordinate(x, y, z);
    }

    public static Coordinate of(double x, double y) {
        return new Coordinate(x, y);
    }

    public static List<Coordinate> of(List<Double> coordinates, int dimension) {
        Objects.requireNonNull(coordinates, "The coordinate list must not be null.");
        if (dimension < 2 || dimension > 3) {
            throw new IllegalArgumentException("The dimension must be 2 or 3.");
        }

        if (coordinates.size() % dimension != 0) {
            throw new IllegalArgumentException("The number of coordinates does not match the dimension.");
        }

        List<Coordinate> coordinateList = new ArrayList<>(coordinates.size() / dimension);
        for (int i = 0; i < coordinates.size(); i += dimension) {
            coordinateList.add(dimension == 3 ?
                    new Coordinate(coordinates.get(i), coordinates.get(i + 1), coordinates.get(i + 2)) :
                    new Coordinate(coordinates.get(i), coordinates.get(i + 1)));
        }

        return coordinateList;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getZ() {
        return z;
    }

    public Coordinate setX(double x) {
        this.x = x;
        return this;
    }

    public Coordinate setY(double y) {
        this.y = y;
        return this;
    }

    public Coordinate setZ(double z) {
        this.z = z;
        dimension = 3;
        return this;
    }

    public int getDimension() {
        return dimension;
    }

    public Coordinate force2D() {
        z = 0;
        dimension = 2;
        return this;
    }

    public Coordinate copy() {
        return dimension == 2 ?
                new Coordinate(x, y) :
                new Coordinate(x, y, z);
    }
}
