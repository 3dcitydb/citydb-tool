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

package org.citydb.database.geometry;

import org.citydb.model.geometry.*;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

public class WKBWriter {
    private final static char[] hexArray = "0123456789ABCDEF".toCharArray();
    private boolean useBigEndian;
    private boolean includeSRID;

    public WKBWriter useBigEndian(boolean useBigEndian) {
        this.useBigEndian = useBigEndian;
        return this;
    }

    public WKBWriter includeSRID(boolean includeSRID) {
        this.includeSRID = includeSRID;
        return this;
    }

    public String write(Geometry<?> geometry) throws GeometryException {
        return write(geometry, false);
    }

    public String write(Geometry<?> geometry, boolean force3D) throws GeometryException {
        if (geometry != null) {
            int srid = includeSRID ? geometry.getSRID().orElse(0) : 0;
            int dimension = force3D ? 3 : geometry.getVertexDimension();
            GeometryType geometryType = geometry.getGeometryType();

            return switch (geometryType) {
                case POINT -> write((Point) geometry, dimension, srid);
                case MULTI_POINT -> write((MultiPoint) geometry, dimension, srid);
                case LINE_STRING -> write((LineString) geometry, dimension, srid);
                case MULTI_LINE_STRING -> write((MultiLineString) geometry, dimension, srid);
                case POLYGON -> write((Polygon) geometry, dimension, srid);
                case MULTI_SURFACE, COMPOSITE_SURFACE, TRIANGULATED_SURFACE ->
                        write((SurfaceCollection<?>) geometry, dimension, srid);
                case SOLID -> write((Solid) geometry, dimension, srid);
                case COMPOSITE_SOLID, MULTI_SOLID -> write((SolidCollection<?>) geometry, dimension, srid);
            };
        } else {
            return null;
        }
    }

    private String write(Point point, int dimension, int srid) {
        ByteBuffer buffer = ByteBuffer.allocate(calculateBytes(point, dimension, srid));
        put(buffer, point, dimension, srid);
        return toHex(buffer.array());
    }

    private String write(MultiPoint multiPoint, int dimension, int srid) {
        ByteBuffer buffer = ByteBuffer.allocate(calculateBytes(multiPoint, dimension, srid));
        put(buffer, multiPoint, dimension, srid);
        return toHex(buffer.array());
    }

    private String write(LineString lineString, int dimension, int srid) {
        ByteBuffer buffer = ByteBuffer.allocate(calculateBytes(lineString, dimension, srid));
        put(buffer, lineString, dimension, srid);
        return toHex(buffer.array());
    }

    private String write(MultiLineString multiLineString, int dimension, int srid) {
        ByteBuffer buffer = ByteBuffer.allocate(calculateBytes(multiLineString, dimension, srid));
        put(buffer, multiLineString, dimension, srid);
        return toHex(buffer.array());
    }

    private String write(Polygon polygon, int dimension, int srid) {
        ByteBuffer buffer = ByteBuffer.allocate(calculateBytes(polygon, dimension, srid));
        put(buffer, polygon, dimension, srid);
        return toHex(buffer.array());
    }

    private String write(SurfaceCollection<?> surfaces, int dimension, int srid) {
        ByteBuffer buffer = ByteBuffer.allocate(calculateBytes(surfaces, dimension, srid));
        put(buffer, surfaces, dimension, srid);
        return toHex(buffer.array());
    }

    private String write(Solid solid, int dimension, int srid) {
        ByteBuffer buffer = ByteBuffer.allocate(calculateBytes(solid, dimension, srid));
        put(buffer, solid, dimension, srid);
        return toHex(buffer.array());
    }

    private String write(SolidCollection<?> solids, int dimension, int srid) {
        ByteBuffer buffer = ByteBuffer.allocate(calculateBytes(solids, dimension, srid));
        put(buffer, solids, dimension, srid);
        return toHex(buffer.array());
    }

    private int calculateBytes(Point point, int dimension, int srid) {
        return getHeaderBytes(srid) + getCoordinatesBytes(1, dimension);
    }

    private void put(ByteBuffer buffer, Point point, int dimension, int srid) {
        putByteOrder(buffer);
        putGeometryType(buffer, WKBConstants.POINT, dimension, srid);
        putCoordinate(buffer, point.getCoordinate(), dimension);
    }

    private int calculateBytes(MultiPoint multiPoint, int dimension, int srid) {
        return 4 + getHeaderBytes(srid) +
                ((getHeaderBytes(srid) + getCoordinatesBytes(1, dimension)) * multiPoint.getPoints().size());
    }

    private void put(ByteBuffer buffer, MultiPoint multiPoint, int dimension, int srid) {
        putByteOrder(buffer);
        putGeometryType(buffer, WKBConstants.MULTIPOINT, dimension, srid);
        buffer.putInt(multiPoint.getPoints().size());
        for (Point point : multiPoint.getPoints()) {
            put(buffer, point, dimension, srid);
        }
    }

    private int calculateBytes(LineString lineString, int dimension, int srid) {
        return 4 + getHeaderBytes(srid) +
                getCoordinatesBytes(lineString.getPoints().size(), dimension);
    }

    private void put(ByteBuffer buffer, LineString lineString, int dimension, int srid) {
        putByteOrder(buffer);
        putGeometryType(buffer, WKBConstants.LINESTRING, dimension, srid);
        if (!lineString.getPoints().isEmpty()) {
            putCoordinates(buffer, lineString.getPoints(), dimension);
        }
    }

    private int calculateBytes(MultiLineString multiLineString, int dimension, int srid) {
        int numberOfBytes = 4 + getHeaderBytes(srid);
        for (LineString lineString : multiLineString.getLineStrings()) {
            numberOfBytes += calculateBytes(lineString, dimension, srid);
        }

        return numberOfBytes;
    }

    private void put(ByteBuffer buffer, MultiLineString multiLineString, int dimension, int srid) {
        putByteOrder(buffer);
        putGeometryType(buffer, WKBConstants.MULTILINESTRING, dimension, srid);
        buffer.putInt(multiLineString.getLineStrings().size());
        for (LineString lineString : multiLineString.getLineStrings()) {
            put(buffer, lineString, dimension, srid);
        }
    }

    private int calculateBytes(Polygon polygon, int dimension, int srid) {
        int numberOfRings = 1;
        int numberOfCoordinates = polygon.getExteriorRing().getPoints().size();

        if (polygon.hasInteriorRings()) {
            numberOfRings += polygon.getInteriorRings().size();
            numberOfCoordinates += polygon.getInteriorRings().stream()
                    .map(LinearRing::getPoints)
                    .mapToInt(List::size)
                    .sum();
        }

        return 4 + numberOfRings * 4 +
                getHeaderBytes(srid) +
                getCoordinatesBytes(numberOfCoordinates, dimension);
    }

    private void put(ByteBuffer buffer, Polygon polygon, int dimension, int srid) {
        putByteOrder(buffer);
        putGeometryType(buffer, WKBConstants.POLYGON, dimension, srid);

        int numberOfRings = 1 + (polygon.hasInteriorRings() ? polygon.getInteriorRings().size() : 0);
        buffer.putInt(numberOfRings);

        putCoordinates(buffer, polygon.getExteriorRing().getPoints(), dimension);
        if (polygon.hasInteriorRings()) {
            for (LinearRing interiorRing : polygon.getInteriorRings()) {
                putCoordinates(buffer, interiorRing.getPoints(), dimension);
            }
        }
    }

    private int calculateBytes(SurfaceCollection<?> surfaces, int dimension, int srid) {
        int numberOfBytes = 4 + getHeaderBytes(srid);
        for (Polygon polygon : surfaces.getPolygons()) {
            numberOfBytes += calculateBytes(polygon, dimension, srid);
        }

        return numberOfBytes;
    }

    private void put(ByteBuffer buffer, SurfaceCollection<?> surfaces, int dimension, int srid) {
        putByteOrder(buffer);
        putGeometryType(buffer, WKBConstants.MULTIPOLYGON, dimension, srid);
        buffer.putInt(surfaces.getPolygons().size());
        for (Polygon polygon : surfaces.getPolygons()) {
            put(buffer, polygon, dimension, srid);
        }
    }

    private int calculateBytes(Solid solid, int dimension, int srid) {
        int numberOfBytes = 4 + getHeaderBytes(srid);
        for (Polygon polygon : solid.getShell().getPolygons()) {
            numberOfBytes += calculateBytes(polygon, dimension, srid);
        }

        return numberOfBytes;
    }

    private void put(ByteBuffer buffer, Solid solid, int dimension, int srid) {
        putByteOrder(buffer);
        putGeometryType(buffer, WKBConstants.POLYHEDRALSURFACE, dimension, srid);
        buffer.putInt(solid.getShell().getPolygons().size());
        for (Polygon polygon : solid.getShell().getPolygons()) {
            put(buffer, polygon, dimension, srid);
        }
    }

    private int calculateBytes(SolidCollection<?> solids, int dimension, int srid) {
        int numberOfBytes = 4 + getHeaderBytes(srid);
        for (Solid solid : solids.getSolids()) {
            numberOfBytes += calculateBytes(solid, dimension, srid);
        }

        return numberOfBytes;
    }

    private void put(ByteBuffer buffer, SolidCollection<?> solids, int dimension, int srid) {
        putByteOrder(buffer);
        putGeometryType(buffer, WKBConstants.GEOMETRYCOLLECTION, dimension, srid);
        buffer.putInt(solids.getSolids().size());
        for (Solid solid : solids.getSolids()) {
            put(buffer, solid, dimension, srid);
        }
    }

    private int getHeaderBytes(int srid) {
        int numberOfBytes = 1 + 4;
        if (srid > 0) {
            numberOfBytes += 4;
        }

        return numberOfBytes;
    }

    private int getCoordinatesBytes(int numberOfCoordinates, int dimension) {
        return 8 * numberOfCoordinates * dimension;
    }

    private void putByteOrder(ByteBuffer buffer) {
        if (useBigEndian) {
            buffer.order(ByteOrder.BIG_ENDIAN);
            buffer.put((byte) 0);
        } else {
            buffer.order(ByteOrder.LITTLE_ENDIAN);
            buffer.put((byte) 1);
        }
    }

    private void putGeometryType(ByteBuffer buffer, int geometryType, int dimension, int srid) {
        int is3D = (dimension == 3) ? 0x80000000 : 0;
        int hasSRID = (srid > 0) ? 0x20000000 : 0;
        buffer.putInt(geometryType | is3D | hasSRID);
        if (srid > 0) {
            buffer.putInt(srid);
        }
    }

    private void putCoordinate(ByteBuffer buffer, Coordinate coordinate, int dimension) {
        buffer.putDouble(coordinate.getX());
        buffer.putDouble(coordinate.getY());
        if (dimension == 3) {
            buffer.putDouble(coordinate.getZ());
        }
    }

    private void putCoordinates(ByteBuffer buffer, List<Coordinate> coordinates, int dimension) {
        buffer.putInt(coordinates.size());
        for (Coordinate coordinate : coordinates) {
            putCoordinate(buffer, coordinate, dimension);
        }
    }

    private String toHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int v = bytes[i] & 0xFF;
            hexChars[i * 2] = hexArray[v >>> 4];
            hexChars[i * 2 + 1] = hexArray[v & 0x0F];
        }

        return new String(hexChars);
    }
}