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

import java.io.IOException;
import java.io.StreamTokenizer;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class WKTParser {
    private static final String COMMA = ",";
    private static final String SEMICOLON = ";";
    private static final String L_PAREN = "(";
    private static final String R_PAREN = ")";
    private static final String EQUAL_SIGN = "=";
    private static final String NAN_SYMBOL = "NaN";

    public Geometry<?> parse(String wkt) throws GeometryException {
        try {
            return wkt != null ? read(createTokenizer(wkt)) : null;
        } catch (IOException e) {
            throw new GeometryException("Failed to tokenize WKT.", e);
        }
    }

    private Geometry<?> read(StreamTokenizer tokenizer) throws GeometryException, IOException {
        Integer srid = readSRID(tokenizer);
        String geometryType = nextWord(tokenizer);
        int dimension = readDimension(tokenizer);

        Geometry<?> geometry = switch (geometryType) {
            case WKTConstants.POINT -> readPoint(tokenizer, dimension);
            case WKTConstants.LINESTRING -> readLineString(tokenizer, dimension);
            case WKTConstants.POLYGON -> readPolygon(tokenizer, dimension);
            case WKTConstants.MULTIPOINT -> readMultiPoint(tokenizer, dimension);
            case WKTConstants.MULTILINESTRING -> readMultiLineString(tokenizer, dimension);
            case WKTConstants.MULTIPOLYGON -> readMultiPolygon(tokenizer, dimension);
            case WKTConstants.POLYHEDRALSURFACE -> readSolid(tokenizer, dimension);
            case WKTConstants.GEOMETRYCOLLECTION -> readGeometryCollection(tokenizer);
            default -> throw new GeometryException("Unsupported geometry type '" + geometryType + "'.");
        };

        return geometry.setSRID(srid);
    }

    private Point readPoint(StreamTokenizer tokenizer, int dimension) throws GeometryException, IOException {
        List<Coordinate> coordinates = getCoordinates(tokenizer, dimension);
        return Point.of(coordinates.get(0));
    }

    private LineString readLineString(StreamTokenizer tokenizer, int dimension) throws GeometryException, IOException {
        return LineString.of(getCoordinates(tokenizer, dimension));
    }

    private LinearRing readLinearRing(StreamTokenizer tokenizer, int dimension) throws GeometryException, IOException {
        return LinearRing.of(getCoordinates(tokenizer, dimension));
    }

    private Polygon readPolygon(StreamTokenizer tokenizer, int dimension) throws GeometryException, IOException {
        if (nextEmptyOrOpener(tokenizer).equals(WKTConstants.EMPTY)) {
            return Polygon.empty();
        }

        LinearRing shell = readLinearRing(tokenizer, dimension);
        List<LinearRing> holes = new ArrayList<>();
        while (nextCloserOrComma(tokenizer).equals(COMMA)) {
            holes.add(readLinearRing(tokenizer, dimension));
        }

        return Polygon.of(shell, holes);
    }

    private List<Polygon> readPolygons(StreamTokenizer tokenizer, int dimension) throws GeometryException, IOException {
        List<Polygon> polygons = new ArrayList<>();
        do {
            polygons.add(readPolygon(tokenizer, dimension));
        } while (nextCloserOrComma(tokenizer).equals(COMMA));

        return polygons;
    }

    private MultiPoint readMultiPoint(StreamTokenizer tokenizer, int dimension) throws GeometryException, IOException {
        if (nextEmptyOrOpener(tokenizer).equals(WKTConstants.EMPTY)) {
            return MultiPoint.empty();
        }

        boolean nested = lookAheadWord(tokenizer).equals(L_PAREN);
        List<Point> points = new ArrayList<>();
        points.add(nested ?
                readPoint(tokenizer, dimension) :
                Point.of(getCoordinate(tokenizer, dimension)));

        while (nextCloserOrComma(tokenizer).equals(COMMA)) {
            points.add(nested ?
                    readPoint(tokenizer, dimension) :
                    Point.of(getCoordinate(tokenizer, dimension)));
        }

        return MultiPoint.of(points);
    }

    private MultiLineString readMultiLineString(StreamTokenizer tokenizer, int dimension) throws GeometryException, IOException {
        if (nextEmptyOrOpener(tokenizer).equals(WKTConstants.EMPTY)) {
            return MultiLineString.empty();
        }

        List<LineString> lineStrings = new ArrayList<>();
        do {
            lineStrings.add(readLineString(tokenizer, dimension));
        } while (nextCloserOrComma(tokenizer).equals(COMMA));

        return MultiLineString.of(lineStrings);
    }

    private MultiSurface readMultiPolygon(StreamTokenizer tokenizer, int dimension) throws GeometryException, IOException {
        return nextEmptyOrOpener(tokenizer).equals(WKTConstants.EMPTY) ?
                MultiSurface.empty() :
                MultiSurface.of(readPolygons(tokenizer, dimension));
    }

    private Solid readSolid(StreamTokenizer tokenizer, int dimension) throws GeometryException, IOException {
        return nextEmptyOrOpener(tokenizer).equals(WKTConstants.EMPTY) ?
                Solid.empty() :
                Solid.of(CompositeSurface.of(readPolygons(tokenizer, dimension)));
    }

    private MultiSolid readGeometryCollection(StreamTokenizer tokenizer) throws GeometryException, IOException {
        if (nextEmptyOrOpener(tokenizer).equals(WKTConstants.EMPTY)) {
            return MultiSolid.empty();
        }

        List<Solid> solids = new ArrayList<>();
        do {
            Geometry<?> geometry = read(tokenizer);
            if (!(geometry instanceof Solid)) {
                throw new GeometryException("Expected Solid but found " + geometry.getGeometryType() + ".");
            }
            solids.add((Solid) geometry);
        } while (nextCloserOrComma(tokenizer).equals(COMMA));

        return MultiSolid.of(solids);
    }

    private Integer readSRID(StreamTokenizer tokenizer) throws GeometryException, IOException {
        if (lookAheadWord(tokenizer).equals(WKTConstants.SRID)) {
            tokenizer.nextToken();
            tokenizer.nextToken();
            int srid = (int) nextNumber(tokenizer);
            tokenizer.nextToken();
            return srid;
        } else {
            return null;
        }
    }

    private int readDimension(StreamTokenizer tokenizer) throws GeometryException, IOException {
        String coordinateFlag = lookAheadWord(tokenizer);
        return switch (coordinateFlag) {
            case WKTConstants.Z -> {
                tokenizer.nextToken();
                yield 3;
            }
            case WKTConstants.M, WKTConstants.ZM ->
                    throw new GeometryException("Unsupported WKT coordinate flag '" + coordinateFlag + "'.");
            default -> 2;
        };
    }

    private Coordinate getCoordinate(StreamTokenizer tokenizer, int dimension) throws GeometryException, IOException {
        double x = nextNumber(tokenizer);
        double y = nextNumber(tokenizer);
        return dimension == 2 ?
                Coordinate.of(x, y) :
                Coordinate.of(x, y, nextNumber(tokenizer));
    }

    private List<Coordinate> getCoordinates(StreamTokenizer tokenizer, int dimension) throws GeometryException, IOException {
        if (nextEmptyOrOpener(tokenizer).equals(WKTConstants.EMPTY)) {
            return Collections.emptyList();
        }

        List<Coordinate> coordinates = new ArrayList<>();
        do {
            coordinates.add(getCoordinate(tokenizer, dimension));
        } while (nextCloserOrComma(tokenizer).equals(COMMA));

        return coordinates;
    }

    private double nextNumber(StreamTokenizer tokenizer) throws GeometryException, IOException {
        if (tokenizer.nextToken() == StreamTokenizer.TT_WORD) {
            if (tokenizer.sval.equalsIgnoreCase(NAN_SYMBOL)) {
                return Double.NaN;
            } else {
                try {
                    return Double.parseDouble(tokenizer.sval);
                } catch (NumberFormatException ex) {
                    throw new GeometryException("Invalid number: " + tokenizer.sval);
                }
            }
        } else {
            throw new GeometryException("Failed to get next number value.");
        }
    }

    private String nextEmptyOrOpener(StreamTokenizer tokenizer) throws GeometryException, IOException {
        String word = nextWord(tokenizer);
        word = switch (word) {
            case WKTConstants.Z, WKTConstants.M, WKTConstants.ZM -> nextWord(tokenizer);
            default -> word;
        };

        return switch (word) {
            case WKTConstants.EMPTY, L_PAREN -> word;
            default -> throw new GeometryException("Expected token " + WKTConstants.EMPTY + " or " + L_PAREN +
                    " but found + " + word + ".");
        };
    }

    private String nextCloserOrComma(StreamTokenizer tokenizer) throws GeometryException, IOException {
        String word = nextWord(tokenizer);
        return switch (word) {
            case COMMA, R_PAREN -> word;
            default -> throw new GeometryException("Expected token " + COMMA + " or " + R_PAREN +
                    " but found + " + word + ".");
        };
    }

    private String nextWord(StreamTokenizer tokenizer) throws GeometryException, IOException {
        int type = tokenizer.nextToken();
        return switch (type) {
            case StreamTokenizer.TT_WORD -> {
                String word = tokenizer.sval.toUpperCase(Locale.ROOT);
                yield word.equals(WKTConstants.EMPTY) ? WKTConstants.EMPTY : word;
            }
            case '(' -> L_PAREN;
            case ')' -> R_PAREN;
            case ',' -> COMMA;
            case ';' -> SEMICOLON;
            case '=' -> EQUAL_SIGN;
            default -> throw new GeometryException("Unsupported WKT token '" + Character.toString(type) + "'.");
        };
    }

    private String lookAheadWord(StreamTokenizer tokenizer) throws GeometryException, IOException {
        String word = nextWord(tokenizer);
        tokenizer.pushBack();
        return word;
    }

    private StreamTokenizer createTokenizer(String wkt) {
        StreamTokenizer tokenizer = new StreamTokenizer(new StringReader(wkt));
        tokenizer.resetSyntax();
        tokenizer.wordChars('a', 'z');
        tokenizer.wordChars('A', 'Z');
        tokenizer.wordChars(128 + 32, 255);
        tokenizer.wordChars('0', '9');
        tokenizer.wordChars('-', '-');
        tokenizer.wordChars('+', '+');
        tokenizer.wordChars('.', '.');
        tokenizer.whitespaceChars(0, ' ');
        tokenizer.commentChar('#');

        return tokenizer;
    }
}
