/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
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

    public String write(SpatialObject object) {
        return write(object, false);
    }

    public String write(SpatialObject object, boolean force3D) {
        return object != null
                ? write(object, force3D, new StringBuffer()).build()
                : null;
    }

    public byte[] writeBinary(SpatialObject object) {
        return writeBinary(object, false);
    }

    public byte[] writeBinary(SpatialObject object, boolean force3D) {
        return object != null
                ? write(object, force3D, new ArrayBuffer()).build()
                : null;
    }

    private <T extends ByteBuffer> T write(SpatialObject object, boolean force3D, T buffer) {
        return switch (object) {
            case Geometry<?> geometry -> write(geometry, force3D, buffer);
            case Envelope envelope -> write(envelope.convertToPolygon(), force3D, buffer);
            case GeometryCollection collection -> write(collection, force3D, buffer);
        };
    }

    private <T extends ByteBuffer> T write(GeometryCollection collection, boolean force3D, T buffer) {
        int srid = includeSRID ? collection.getSRID().orElse(0) : 0;
        int dimension = force3D ? 3 : collection.getVertexDimension();
        int numberOfBytes = 4 + getHeaderBytes(srid);
        for (Geometry<?> geometry : collection.getGeometries()) {
            numberOfBytes += calculateBytes(geometry, dimension, srid);
        }

        buffer.allocate(numberOfBytes);
        buffer.putByteOrder();
        putGeometryType(buffer, WKBConstants.GEOMETRYCOLLECTION, dimension, srid);
        buffer.putInt(collection.getGeometries().size());
        for (Geometry<?> geometry : collection.getGeometries()) {
            write(geometry, dimension, srid, buffer);
        }

        return buffer;
    }

    private <T extends ByteBuffer> T write(Geometry<?> geometry, boolean force3D, T buffer) {
        int srid = includeSRID ? geometry.getSRID().orElse(0) : 0;
        int dimension = force3D ? 3 : geometry.getVertexDimension();
        buffer.allocate(calculateBytes(geometry, dimension, srid));
        write(geometry, dimension, srid, buffer);
        return buffer;
    }

    private void write(Geometry<?> geometry, int dimension, int srid, ByteBuffer buffer) {
        switch (geometry) {
            case Point point -> write(point, dimension, srid, buffer);
            case MultiPoint multiPoint -> write(multiPoint, dimension, srid, buffer);
            case LineString lineString -> write(lineString, dimension, srid, buffer);
            case MultiLineString multiLineString -> write(multiLineString, dimension, srid, buffer);
            case Polygon polygon -> write(polygon, dimension, srid, buffer);
            case SurfaceCollection<?> surfaceCollection -> write(surfaceCollection, dimension, srid, buffer);
            case Solid solid -> write(solid, dimension, srid, buffer);
            case SolidCollection<?> solidCollection -> write(solidCollection, dimension, srid, buffer);
        }
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
        return switch (geometry) {
            case Point point -> calculateBytes(point, dimension, srid);
            case MultiPoint multiPoint -> calculateBytes(multiPoint, dimension, srid);
            case LineString lineString -> calculateBytes(lineString, dimension, srid);
            case MultiLineString multiLineString -> calculateBytes(multiLineString, dimension, srid);
            case Polygon polygon -> calculateBytes(polygon, dimension, srid);
            case SurfaceCollection<?> surfaceCollection -> calculateBytes(surfaceCollection, dimension, srid);
            case Solid solid -> calculateBytes(solid, dimension, srid);
            case SolidCollection<?> solidCollection -> calculateBytes(solidCollection, dimension, srid);
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
        private final int[] indexes;
        private int pos;

        ByteBuffer() {
            indexes = WKBWriter.this.useBigEndian
                    ? new int[]{0, 2, 0, 1, 2, 3, 4, 5, 6, 7}
                    : new int[]{1, 6, 7, 6, 5, 4, 3, 2, 1, 0};
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
        private static final char[] hexArray = "0123456789ABCDEF".toCharArray();
        private char[] data;

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
        private byte[] data;

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