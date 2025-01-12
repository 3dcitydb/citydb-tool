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

import java.util.ArrayList;
import java.util.List;

public class WKBParser {

    public Geometry<?> parse(String wkb) throws GeometryException {
        return wkb != null ? read(new StringBuffer(wkb)) : null;
    }

    public Geometry<?> parse(Object wkb) throws GeometryException {
        return wkb != null ? read(new StringBuffer(wkb.toString())) : null;
    }

    public Geometry<?> parse(byte[] bytes) throws GeometryException {
        return bytes != null ? read(new ArrayBuffer(bytes)) : null;
    }

    private Geometry<?> read(ByteBuffer buffer) throws GeometryException {
        buffer.order(buffer.getByte());

        int typeInt = buffer.getInt();
        int geometryType = typeInt & 0xff;

        boolean hasZ = (typeInt & 0x80000000) != 0;
        int dimension = hasZ ? 3 : 2;

        boolean hasSRID = (typeInt & 0x20000000) != 0;
        Integer srid = hasSRID ? buffer.getInt() : null;

        Geometry<?> geometry = switch (geometryType) {
            case WKBConstants.POINT -> readPoint(buffer, dimension);
            case WKBConstants.LINESTRING -> readLineString(buffer, dimension);
            case WKBConstants.POLYGON -> readPolygon(buffer, dimension);
            case WKBConstants.MULTIPOINT -> readMultiPoint(buffer);
            case WKBConstants.MULTILINESTRING -> readMultiLineString(buffer);
            case WKBConstants.MULTIPOLYGON -> readMultiPolygon(buffer);
            case WKBConstants.POLYHEDRALSURFACE -> readSolid(buffer);
            case WKBConstants.GEOMETRYCOLLECTION -> readGeometryCollection(buffer);
            default -> throw new GeometryException("Unsupported geometry type '" + geometryType + "'.");
        };

        return geometry.setSRID(srid);
    }

    private Point readPoint(ByteBuffer buffer, int dimension) {
        return Point.of(getCoordinate(buffer, dimension));
    }

    private LineString readLineString(ByteBuffer buffer, int dimension) {
        return LineString.of(getCoordinates(buffer, buffer.getInt(), dimension));
    }

    private LinearRing readLinearRing(ByteBuffer buffer, int dimension) {
        return LinearRing.of(getCoordinates(buffer, buffer.getInt(), dimension));
    }

    private Polygon readPolygon(ByteBuffer buffer, int dimension) {
        int size = buffer.getInt();
        LinearRing shell = size > 0 ? readLinearRing(buffer, dimension) : LinearRing.empty();

        LinearRing[] holes = null;
        if (size > 1) {
            holes = new LinearRing[size - 1];
            for (int i = 0; i < size - 1; i++) {
                holes[i] = readLinearRing(buffer, dimension);
            }
        }

        return Polygon.of(shell, holes);
    }

    private Polygon[] readPolygons(ByteBuffer buffer) throws GeometryException {
        int size = buffer.getInt();
        Polygon[] polygons = new Polygon[size];

        for (int i = 0; i < size; i++) {
            Geometry<?> geometry = read(buffer);
            if (!(geometry instanceof Polygon)) {
                throw new GeometryException("Expected Polygon but found " + geometry.getGeometryType() + ".");
            }
            polygons[i] = (Polygon) geometry;
        }

        return polygons;
    }

    private MultiPoint readMultiPoint(ByteBuffer buffer) throws GeometryException {
        int size = buffer.getInt();
        Point[] points = new Point[size];

        for (int i = 0; i < size; i++) {
            Geometry<?> geometry = read(buffer);
            if (!(geometry instanceof Point)) {
                throw new GeometryException("Expected Point but found " + geometry.getGeometryType() + ".");
            }
            points[i] = (Point) geometry;
        }

        return MultiPoint.of(points);
    }

    private MultiLineString readMultiLineString(ByteBuffer buffer) throws GeometryException {
        int size = buffer.getInt();
        LineString[] lineStrings = new LineString[size];

        for (int i = 0; i < size; i++) {
            Geometry<?> geometry = read(buffer);
            if (!(geometry instanceof LineString)) {
                throw new GeometryException("Expected LineString but found " + geometry.getGeometryType() + ".");
            }
            lineStrings[i] = (LineString) geometry;
        }

        return MultiLineString.of(lineStrings);
    }

    private MultiSurface readMultiPolygon(ByteBuffer buffer) throws GeometryException {
        return MultiSurface.of(readPolygons(buffer));
    }

    private Solid readSolid(ByteBuffer buffer) throws GeometryException {
        return Solid.of(CompositeSurface.of(readPolygons(buffer)));
    }

    private MultiSolid readGeometryCollection(ByteBuffer buffer) throws GeometryException {
        int size = buffer.getInt();
        Solid[] solids = new Solid[size];

        for (int i = 0; i < size; i++) {
            Geometry<?> geometry = read(buffer);
            if (!(geometry instanceof Solid)) {
                throw new GeometryException("Expected Solid but found " + geometry.getGeometryType() + ".");
            }
            solids[i] = (Solid) geometry;
        }

        return MultiSolid.of(solids);
    }

    private List<Coordinate> getCoordinates(ByteBuffer buffer, int size, int dimension) {
        List<Coordinate> coordinates = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            coordinates.add(getCoordinate(buffer, dimension));
        }

        return coordinates;
    }

    private Coordinate getCoordinate(ByteBuffer buffer, int dimension) {
        double x = buffer.getDouble();
        double y = buffer.getDouble();
        return dimension == 2 ?
                Coordinate.of(x, y) :
                Coordinate.of(x, y, buffer.getDouble());
    }

    private static abstract class ByteBuffer {
        final int[] bigEndian = new int[]{1, 0, 1, 2, 3, 4, 5, 6, 7};
        final int[] littleEndian = new int[]{5, 7, 6, 5, 4, 3, 2, 1, 0};
        int[] indexes = bigEndian;
        int pos;

        abstract int get(int pos);

        void order(byte order) {
            indexes = order == 0 ? bigEndian : littleEndian;
        }

        byte getByte() {
            return (byte) get(pos++);
        }

        int getInt() {
            int low = indexes[0];
            int value = (get(pos + indexes[low]) << 24) + (get(pos + indexes[low + 1]) << 16) +
                    (get(pos + indexes[low + 2]) << 8) + get(pos + indexes[low + 3]);
            pos += 4;
            return value;
        }

        long getLong() {
            long value = ((long) get(pos + indexes[1]) << 56) + ((long) get(pos + indexes[2]) << 48) +
                    ((long) get(pos + indexes[3]) << 40) + ((long) get(pos + indexes[4]) << 32) +
                    ((long) get(pos + indexes[5]) << 24) + ((long) get(pos + indexes[6]) << 16) +
                    ((long) get(pos + indexes[7]) << 8) + ((long) get(pos + indexes[8]));
            pos += 8;
            return value;
        }

        double getDouble() {
            return Double.longBitsToDouble(getLong());
        }
    }

    private static class StringBuffer extends ByteBuffer {
        final String data;

        StringBuffer(String data) {
            this.data = data;
        }

        @Override
        int get(int pos) {
            pos *= 2;
            return (Character.digit(data.charAt(pos), 16) << 4) +
                    Character.digit(data.charAt(pos + 1), 16);
        }
    }

    private static class ArrayBuffer extends ByteBuffer {
        final byte[] data;

        ArrayBuffer(byte[] data) {
            this.data = data;
        }

        @Override
        int get(int pos) {
            return data[pos] & 0xFF;
        }
    }
}
