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

package org.citydb.database.geometry;

import org.citydb.model.geometry.*;

import java.util.List;

public class WKBWriter {
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

    public String write(Geometry<?> geometry) {
        return write(geometry, false);
    }

    public String write(Geometry<?> geometry, boolean force3D) {
        return geometry != null ?
                write(geometry, force3D, new StringBuffer()).build() :
                null;
    }

    public byte[] writeBinary(Geometry<?> geometry) {
        return writeBinary(geometry, false);
    }

    public byte[] writeBinary(Geometry<?> geometry, boolean force3D) {
        return geometry != null ?
                write(geometry, force3D, new ArrayBuffer()).build() :
                null;
    }

    private <T extends ByteBuffer> T write(Geometry<?> geometry, boolean force3D, T buffer) {
        int srid = includeSRID ? geometry.getSRID().orElse(0) : 0;
        int dimension = force3D ? 3 : geometry.getVertexDimension();
        buffer.allocate(calculateBytes(geometry, dimension, srid));

        switch (geometry.getGeometryType()) {
            case POINT -> write((Point) geometry, dimension, srid, buffer);
            case MULTI_POINT -> write((MultiPoint) geometry, dimension, srid, buffer);
            case LINE_STRING -> write((LineString) geometry, dimension, srid, buffer);
            case MULTI_LINE_STRING -> write((MultiLineString) geometry, dimension, srid, buffer);
            case POLYGON -> write((Polygon) geometry, dimension, srid, buffer);
            case MULTI_SURFACE, COMPOSITE_SURFACE, TRIANGULATED_SURFACE ->
                    write((SurfaceCollection<?>) geometry, dimension, srid, buffer);
            case SOLID -> write((Solid) geometry, dimension, srid, buffer);
            case COMPOSITE_SOLID, MULTI_SOLID -> write((SolidCollection<?>) geometry, dimension, srid, buffer);
        }

        return buffer;
    }

    private void write(Point point, int dimension, int srid, ByteBuffer buffer) {
        buffer.putByteOrder();
        putGeometryType(buffer, WKBConstants.POINT, dimension, srid);
        putCoordinate(buffer, point.getCoordinate(), dimension);
    }

    private void write(MultiPoint multiPoint, int dimension, int srid, ByteBuffer buffer) {
        buffer.putByteOrder();
        putGeometryType(buffer, WKBConstants.MULTIPOINT, dimension, srid);
        buffer.putInt(multiPoint.getPoints().size());
        for (Point point : multiPoint.getPoints()) {
            write(point, dimension, srid, buffer);
        }
    }

    private void write(MultiLineString multiLineString, int dimension, int srid, ByteBuffer buffer) {
        buffer.putByteOrder();
        putGeometryType(buffer, WKBConstants.MULTILINESTRING, dimension, srid);
        buffer.putInt(multiLineString.getLineStrings().size());
        for (LineString lineString : multiLineString.getLineStrings()) {
            write(lineString, dimension, srid, buffer);
        }
    }

    private void write(LineString lineString, int dimension, int srid, ByteBuffer buffer) {
        buffer.putByteOrder();
        putGeometryType(buffer, WKBConstants.LINESTRING, dimension, srid);
        if (!lineString.getPoints().isEmpty()) {
            putCoordinates(buffer, lineString.getPoints(), dimension);
        }
    }

    private void write(Polygon polygon, int dimension, int srid, ByteBuffer buffer) {
        buffer.putByteOrder();
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

    private void write(SurfaceCollection<?> surfaces, int dimension, int srid, ByteBuffer buffer) {
        buffer.putByteOrder();
        putGeometryType(buffer, WKBConstants.MULTIPOLYGON, dimension, srid);
        buffer.putInt(surfaces.getPolygons().size());
        for (Polygon polygon : surfaces.getPolygons()) {
            write(polygon, dimension, srid, buffer);
        }
    }

    private void write(Solid solid, int dimension, int srid, ByteBuffer buffer) {
        buffer.putByteOrder();
        putGeometryType(buffer, WKBConstants.POLYHEDRALSURFACE, dimension, srid);
        buffer.putInt(solid.getShell().getPolygons().size());
        for (Polygon polygon : solid.getShell().getPolygons()) {
            write(polygon, dimension, srid, buffer);
        }
    }

    private void write(SolidCollection<?> solids, int dimension, int srid, ByteBuffer buffer) {
        buffer.putByteOrder();
        putGeometryType(buffer, WKBConstants.GEOMETRYCOLLECTION, dimension, srid);
        buffer.putInt(solids.getSolids().size());
        for (Solid solid : solids.getSolids()) {
            write(solid, dimension, srid, buffer);
        }
    }

    private int calculateBytes(Geometry<?> geometry, int dimension, int srid) {
        return switch (geometry.getGeometryType()) {
            case POINT -> calculateBytes((Point) geometry, dimension, srid);
            case MULTI_POINT -> calculateBytes((MultiPoint) geometry, dimension, srid);
            case LINE_STRING -> calculateBytes((LineString) geometry, dimension, srid);
            case MULTI_LINE_STRING -> calculateBytes((MultiLineString) geometry, dimension, srid);
            case POLYGON -> calculateBytes((Polygon) geometry, dimension, srid);
            case MULTI_SURFACE, COMPOSITE_SURFACE, TRIANGULATED_SURFACE ->
                    calculateBytes((SurfaceCollection<?>) geometry, dimension, srid);
            case SOLID -> calculateBytes((Solid) geometry, dimension, srid);
            case COMPOSITE_SOLID, MULTI_SOLID -> calculateBytes((SolidCollection<?>) geometry, dimension, srid);
        };
    }

    private int calculateBytes(Point point, int dimension, int srid) {
        return getHeaderBytes(srid) + getCoordinatesBytes(1, dimension);
    }

    private int calculateBytes(MultiPoint multiPoint, int dimension, int srid) {
        return 4 + getHeaderBytes(srid) +
                ((getHeaderBytes(srid) + getCoordinatesBytes(1, dimension)) * multiPoint.getPoints().size());
    }

    private int calculateBytes(LineString lineString, int dimension, int srid) {
        return 4 + getHeaderBytes(srid) +
                getCoordinatesBytes(lineString.getPoints().size(), dimension);
    }

    private int calculateBytes(MultiLineString multiLineString, int dimension, int srid) {
        int numberOfBytes = 4 + getHeaderBytes(srid);
        for (LineString lineString : multiLineString.getLineStrings()) {
            numberOfBytes += calculateBytes(lineString, dimension, srid);
        }

        return numberOfBytes;
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

    private int calculateBytes(SurfaceCollection<?> surfaces, int dimension, int srid) {
        int numberOfBytes = 4 + getHeaderBytes(srid);
        for (Polygon polygon : surfaces.getPolygons()) {
            numberOfBytes += calculateBytes(polygon, dimension, srid);
        }

        return numberOfBytes;
    }

    private int calculateBytes(Solid solid, int dimension, int srid) {
        int numberOfBytes = 4 + getHeaderBytes(srid);
        for (Polygon polygon : solid.getShell().getPolygons()) {
            numberOfBytes += calculateBytes(polygon, dimension, srid);
        }

        return numberOfBytes;
    }

    private int calculateBytes(SolidCollection<?> solids, int dimension, int srid) {
        int numberOfBytes = 4 + getHeaderBytes(srid);
        for (Solid solid : solids.getSolids()) {
            numberOfBytes += calculateBytes(solid, dimension, srid);
        }

        return numberOfBytes;
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

    private void putGeometryType(ByteBuffer buffer, int geometryType, int dimension, int srid) {
        int is3D = dimension == 3 ? 0x80000000 : 0;
        int hasSRID = srid > 0 ? 0x20000000 : 0;
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

    private abstract class ByteBuffer {
        final int[] indexes;
        int pos;

        ByteBuffer() {
            indexes = WKBWriter.this.useBigEndian ?
                    new int[]{0, 2, 0, 1, 2, 3, 4, 5, 6, 7} :
                    new int[]{1, 6, 7, 6, 5, 4, 3, 2, 1, 0};
        }

        abstract void allocate(int length);

        abstract void put(byte b, int index);

        void putByteOrder() {
            put((byte) indexes[0]);
        }

        void put(byte b) {
            put(b, pos++);
        }

        void putInt(int value) {
            int low = indexes[1];
            put((byte) (value >>> 24), pos + indexes[low]);
            put((byte) (value >>> 16), pos + indexes[low + 1]);
            put((byte) (value >>> 8), pos + indexes[low + 2]);
            put((byte) value, pos + indexes[low + 3]);
            pos += 4;
        }

        void putLong(long value) {
            put((byte) (value >>> 56), pos + indexes[2]);
            put((byte) (value >>> 48), pos + indexes[3]);
            put((byte) (value >>> 40), pos + indexes[4]);
            put((byte) (value >>> 32), pos + indexes[5]);
            put((byte) (value >>> 24), pos + indexes[6]);
            put((byte) (value >>> 16), pos + indexes[7]);
            put((byte) (value >>> 8), pos + indexes[8]);
            put((byte) value, pos + indexes[9]);
            pos += 8;
        }

        void putDouble(double value) {
            putLong(Double.doubleToLongBits(value));
        }
    }

    private class StringBuffer extends ByteBuffer {
        final static char[] hexArray = "0123456789ABCDEF".toCharArray();
        char[] data;

        @Override
        void allocate(int length) {
            data = new char[length * 2];
        }

        @Override
        void put(byte b, int index) {
            index *= 2;
            data[index] = hexArray[(b >>> 4) & 0xF];
            data[index + 1] = hexArray[b & 0xF];
        }

        String build() {
            return new String(data);
        }
    }

    private class ArrayBuffer extends ByteBuffer {
        byte[] data;

        @Override
        void allocate(int length) {
            data = new byte[length];
        }

        @Override
        void put(byte b, int index) {
            data[index] = b;
        }

        byte[] build() {
            return data;
        }
    }
}