/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.query.filter.encoding;

import org.citydb.core.json.Json;
import org.citydb.model.geometry.*;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;

public class GeoJSONParser {

    GeoJSONParser() {
    }

    Geometry<?> parse(ObjectNode geometry) throws FilterParseException {
        try {
            String type = Json.getString(geometry, JSONToken.TYPE.value());
            ArrayNode coordinates = getArray(geometry, JSONToken.COORDINATES.value());
            if (coordinates == null) {
                coordinates = Json.arrayNode();
            }

            return switch (JSONToken.of(type)) {
                case POINT -> readPoint(coordinates);
                case LINESTRING -> readLineString(coordinates);
                case POLYGON -> readPolygon(coordinates);
                case MULTIPOINT -> readMultiPoint(coordinates);
                case MULTILINESTRING -> readMultiLineString(coordinates);
                case MULTIPOLYGON -> readMultiSurface(coordinates);
                default -> throw new FilterParseException("Unsupported geometry type '" + type + "'.");
            };
        } catch (JacksonException e) {
            throw new FilterParseException("Failed to parse GeoJSON geometry.", e);
        }
    }

    List<Geometry<?>> parseCollection(ObjectNode collection) throws FilterParseException {
        try {
            ArrayNode geometries = getArray(collection, JSONToken.GEOMETRIES.value());
            if (geometries == null || geometries.isEmpty()) {
                throw new FilterParseException("Found empty geometries array for geometry collection.");
            }

            List<Geometry<?>> members = new ArrayList<>(geometries.size());
            for (JsonNode element : geometries) {
                if (element instanceof ObjectNode geometry) {
                    if (JSONToken.of(Json.getString(geometry, JSONToken.TYPE.value())) == JSONToken.GEOMETRYCOLLECTION) {
                        throw new FilterParseException("Nested geometry collections are not supported.");
                    }

                    members.add(parse(geometry));
                } else {
                    throw new FilterParseException("Found invalid geometry '" + element + "'.");
                }
            }

            return members;
        } catch (JacksonException e) {
            throw new FilterParseException("Failed to parse GeoJSON geometry collection.", e);
        }
    }

    private Point readPoint(ArrayNode array) throws FilterParseException {
        return Point.of(readCoordinate(array));
    }

    private LineString readLineString(ArrayNode array) throws FilterParseException {
        return LineString.of(readCoordinates(array));
    }

    private Polygon readPolygon(ArrayNode array) throws FilterParseException {
        List<LinearRing> rings = new ArrayList<>(array.size());
        for (JsonNode element : array) {
            if (element instanceof ArrayNode ring) {
                rings.add(LinearRing.of(readCoordinates(ring)));
            } else {
                throw new FilterParseException("Found invalid linear ring '" + element + "'.");
            }
        }

        if (!rings.isEmpty()) {
            return rings.size() == 1
                    ? Polygon.of(rings.get(0))
                    : Polygon.of(rings.get(0), rings.subList(1, rings.size()));
        } else {
            throw new FilterParseException("Found empty ring array for polygon geometry.");
        }
    }

    private MultiPoint readMultiPoint(ArrayNode array) throws FilterParseException {
        List<Point> points = new ArrayList<>(array.size());
        for (JsonNode element : array) {
            if (element instanceof ArrayNode point) {
                points.add(readPoint(point));
            } else {
                throw new FilterParseException("Found invalid point '" + element + "'.");
            }
        }

        if (!points.isEmpty()) {
            return MultiPoint.of(points);
        } else {
            throw new FilterParseException("Found empty point array for multipoint geometry.");
        }
    }

    private MultiLineString readMultiLineString(ArrayNode array) throws FilterParseException {
        List<LineString> lineStrings = new ArrayList<>(array.size());
        for (JsonNode element : array) {
            if (element instanceof ArrayNode lineString) {
                lineStrings.add(readLineString(lineString));
            } else {
                throw new FilterParseException("Found invalid linestring '" + element + "'.");
            }
        }

        if (!lineStrings.isEmpty()) {
            return MultiLineString.of(lineStrings);
        } else {
            throw new FilterParseException("Found empty linestring array for multipoint geometry.");
        }
    }

    private MultiSurface readMultiSurface(ArrayNode array) throws FilterParseException {
        List<Polygon> polygons = new ArrayList<>(array.size());
        for (JsonNode element : array) {
            if (element instanceof ArrayNode polygon) {
                polygons.add(readPolygon(polygon));
            } else {
                throw new FilterParseException("Found invalid polygon '" + element + "'.");
            }
        }

        if (!polygons.isEmpty()) {
            return MultiSurface.of(polygons);
        } else {
            throw new FilterParseException("Found empty polygon array for multipoint geometry.");
        }
    }

    private List<Coordinate> readCoordinates(ArrayNode array) throws FilterParseException {
        List<Coordinate> coordinates = new ArrayList<>(array.size());
        for (JsonNode element : array) {
            if (element instanceof ArrayNode coordinate) {
                coordinates.add(readCoordinate(coordinate));
            } else {
                throw new FilterParseException("Found invalid coordinates array '" + element + "'.");
            }
        }

        return coordinates;
    }

    private Coordinate readCoordinate(ArrayNode array) throws FilterParseException {
        if (array.size() > 1) {
            double x = readNumber(array.get(0));
            double y = readNumber(array.get(1));
            return array.size() > 2
                    ? Coordinate.of(x, y, readNumber(array.get(2)))
                    : Coordinate.of(x, y);
        } else {
            throw new FilterParseException("Found invalid coordinate '" + array + "'.");
        }
    }

    private double readNumber(JsonNode value) throws FilterParseException {
        if (value.isNumber()) {
            return value.doubleValue();
        } else {
            throw new FilterParseException("Failed to parse " + value + " as coordinate value.");
        }
    }

    private ArrayNode getArray(JsonNode object, String name) throws FilterParseException {
        JsonNode value = object.get(name);
        if (value == null || value.isNull()) {
            return null;
        } else if (value instanceof ArrayNode array) {
            return array;
        } else {
            throw new FilterParseException("Failed to parse " + value + " as JSON array.");
        }
    }
}
