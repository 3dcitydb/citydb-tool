/*
 * citydb-tool - Command-line tool for the 3D City Database
 * https://www.3dcitydb.org/
 *
 * Copyright 2022-2023
 * Virtual City Systems, Germany
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

    Coordinate force2D() {
        z = 0;
        dimension = 2;
        return this;
    }
}
