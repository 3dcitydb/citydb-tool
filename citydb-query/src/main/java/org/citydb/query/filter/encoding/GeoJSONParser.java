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

package org.citydb.query.filter.encoding;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONException;
import com.alibaba.fastjson2.JSONObject;
import org.citydb.model.geometry.*;

import java.util.ArrayList;
import java.util.List;

public class GeoJSONParser {

    GeoJSONParser() {
    }

    Geometry<?> parse(JSONObject geometry) throws FilterParseException {
        try {
            String type = geometry.getString(JSONToken.TYPE.value());
            JSONArray coordinates = geometry.getJSONArray("coordinates");
            if (coordinates == null) {
                coordinates = new JSONArray();
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
        } catch (JSONException e) {
            throw new FilterParseException("Failed to parse GeoJSON geometry.", e);
        }
    }

    private Point readPoint(JSONArray array) throws FilterParseException {
        return Point.of(readCoordinate(array));
    }

    private LineString readLineString(JSONArray array) throws FilterParseException {
        return LineString.of(readCoordinates(array));
    }

    private Polygon readPolygon(JSONArray array) throws FilterParseException {
        List<LinearRing> rings = new ArrayList<>(array.size());
        for (Object element : array) {
            if (element instanceof JSONArray ring) {
                rings.add(LinearRing.of(readCoordinates(ring)));
            } else {
                throw new FilterParseException("Found invalid linear ring '" + element + "'.");
            }
        }

        if (!rings.isEmpty()) {
            return rings.size() == 1 ?
                    Polygon.of(rings.get(0)) :
                    Polygon.of(rings.get(0), rings.subList(1, rings.size()));
        } else {
            throw new FilterParseException("Found empty ring array for polygon geometry.");
        }
    }

    private MultiPoint readMultiPoint(JSONArray array) throws FilterParseException {
        List<Point> points = new ArrayList<>(array.size());
        for (Object element : array) {
            if (element instanceof JSONArray point) {
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

    private MultiLineString readMultiLineString(JSONArray array) throws FilterParseException {
        List<LineString> lineStrings = new ArrayList<>(array.size());
        for (Object element : array) {
            if (element instanceof JSONArray lineString) {
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

    private MultiSurface readMultiSurface(JSONArray array) throws FilterParseException {
        List<Polygon> polygons = new ArrayList<>(array.size());
        for (Object element : array) {
            if (element instanceof JSONArray polygon) {
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

    private List<Coordinate> readCoordinates(JSONArray array) throws FilterParseException {
        List<Coordinate> coordinates = new ArrayList<>(array.size());
        for (Object element : array) {
            if (element instanceof JSONArray coordinate) {
                coordinates.add(readCoordinate(coordinate));
            } else {
                throw new FilterParseException("Found invalid coordinates array '" + element + "'.");
            }
        }

        return coordinates;
    }

    private Coordinate readCoordinate(JSONArray array) throws FilterParseException {
        if (array.size() > 1) {
            double x = readNumber(array.get(0));
            double y = readNumber(array.get(1));
            return array.size() > 2 ?
                    Coordinate.of(x, y, readNumber(array.get(2))) :
                    Coordinate.of(x, y);
        } else {
            throw new FilterParseException("Found invalid coordinate '" + array + "'.");
        }
    }

    private double readNumber(Object value) throws FilterParseException {
        if (value instanceof Number number) {
            return number.doubleValue();
        } else {
            throw new FilterParseException("Failed to parse '" + value + "' as coordinate value.");
        }
    }
}
