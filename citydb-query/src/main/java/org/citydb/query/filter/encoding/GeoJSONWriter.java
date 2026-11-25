/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.query.filter.encoding;

import org.citydb.model.geometry.*;
import tools.jackson.core.JsonGenerator;

import java.util.List;

public class GeoJSONWriter {
    private final JsonGenerator jsonWriter;

    GeoJSONWriter(JsonGenerator jsonWriter) {
        this.jsonWriter = jsonWriter;
    }

    void write(Geometry<?> geometry) {
        if (geometry != null) {
            switch (geometry.getGeometryType()) {
                case POINT -> writePoint((Point) geometry);
                case LINE_STRING -> writeLineString((LineString) geometry);
                case POLYGON -> writePolygon((Polygon) geometry);
                case MULTI_POINT -> writeMultiPoint((MultiPoint) geometry);
                case MULTI_LINE_STRING -> writeMultiLineString((MultiLineString) geometry);
                case MULTI_SURFACE, COMPOSITE_SURFACE, TRIANGULATED_SURFACE ->
                        writeMultiSurface((SurfaceCollection<?>) geometry);
            }
        }
    }

    void writeCollection(List<? extends Geometry<?>> members) {
        startGeometry(JSONToken.GEOMETRYCOLLECTION);
        jsonWriter.writeName(JSONToken.GEOMETRIES.value());
        jsonWriter.writeStartArray();
        for (Geometry<?> member : members) {
            write(member);
        }
        jsonWriter.writeEndArray();
        endGeometry();
    }

    private void writePoint(Point point) {
        startGeometry(JSONToken.POINT);
        jsonWriter.writeName(JSONToken.COORDINATES.value());
        writeCoordinate(point.getCoordinate());
        endGeometry();
    }

    private void writeLineString(LineString lineString) {
        startGeometry(JSONToken.LINESTRING);
        startCoordinates();
        writeCoordinates(lineString);
        endCoordinates();
        endGeometry();
    }

    private void writePolygon(Polygon polygon) {
        startGeometry(JSONToken.POLYGON);
        startCoordinates();
        writeCoordinates(polygon);
        endCoordinates();
        endGeometry();
    }

    private void writeMultiPoint(MultiPoint multiPoint) {
        startGeometry(JSONToken.MULTIPOINT);
        startCoordinates();

        for (Point point : multiPoint.getPoints()) {
            writeCoordinate(point.getCoordinate());
        }

        endCoordinates();
        endGeometry();
    }

    private void writeMultiLineString(MultiLineString multiLineString) {
        startGeometry(JSONToken.MULTILINESTRING);
        startCoordinates();

        if (!multiLineString.getLineStrings().isEmpty()) {
            jsonWriter.writeStartArray();
            for (LineString lineString : multiLineString.getLineStrings()) {
                writeCoordinates(lineString);
            }
            jsonWriter.writeEndArray();
        }

        endCoordinates();
        endGeometry();
    }

    private void writeMultiSurface(SurfaceCollection<?> surfaceCollection) {
        startGeometry(JSONToken.MULTIPOLYGON);
        startCoordinates();

        if (!surfaceCollection.getPolygons().isEmpty()) {
            jsonWriter.writeStartArray();
            for (Polygon polygon : surfaceCollection.getPolygons()) {
                writeCoordinates(polygon);
            }
            jsonWriter.writeEndArray();
        }

        endCoordinates();
        endGeometry();
    }

    private void writeCoordinate(Coordinate coordinate) {
        writeDoubles(jsonWriter, coordinate.getDimension() == 2
                ? List.of(coordinate.getX(), coordinate.getY())
                : List.of(coordinate.getX(), coordinate.getY(), coordinate.getZ()));
    }


    private void writeCoordinates(LineString lineString) {
        for (Coordinate coordinate : lineString.getPoints()) {
            writeCoordinate(coordinate);
        }
    }

    private void writeCoordinates(LinearRing linearRing) {
        for (Coordinate coordinate : linearRing.getPoints()) {
            writeCoordinate(coordinate);
        }
    }

    private void writeCoordinates(Polygon polygon) {
        jsonWriter.writeStartArray();
        writeCoordinates(polygon.getExteriorRing());
        jsonWriter.writeEndArray();

        if (polygon.hasInteriorRings()) {
            for (LinearRing ring : polygon.getInteriorRings()) {
                jsonWriter.writeStartArray();
                writeCoordinates(ring);
                jsonWriter.writeEndArray();
            }
        }
    }

    private void startGeometry(JSONToken type) {
        jsonWriter.writeStartObject();
        jsonWriter.writeName(JSONToken.TYPE.value());
        jsonWriter.writeString(type.value());
    }

    private void endGeometry() {
        jsonWriter.writeEndObject();
    }

    private void startCoordinates() {
        jsonWriter.writeName(JSONToken.COORDINATES.value());
        jsonWriter.writeStartArray();
    }

    private void endCoordinates() {
        jsonWriter.writeEndArray();
    }

    static void writeDoubles(JsonGenerator jsonWriter, List<Double> values) {
        jsonWriter.writeStartArray();
        for (double value : values) {
            jsonWriter.writeNumber(value);
        }
        jsonWriter.writeEndArray();
    }
}
