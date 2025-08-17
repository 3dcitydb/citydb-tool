/*
 * citydb-tool - Command-line tool for the 3D City Database
 * https://www.3dcitydb.org/
 *
 * Copyright 2022-2025
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
import java.util.stream.Collectors;

public class WKTWriter {
    private boolean includeSRID;

    public WKTWriter includeSRID(boolean includeSRID) {
        this.includeSRID = includeSRID;
        return this;
    }

    public String write(Geometry<?> geometry) throws GeometryException {
        return write(geometry, false);
    }

    public String write(Geometry<?> geometry, boolean force3D) throws GeometryException {
        if (geometry != null) {
            StringBuilder wkt = new StringBuilder();

            GeometryType geometryType = geometry.getGeometryType();
            int dimension = force3D ? 3 : geometry.getVertexDimension();
            if (includeSRID) {
                writeSRID(wkt, geometry);
            }

            switch (geometryType) {
                case POINT:
                    write(wkt, (Point) geometry, dimension);
                    break;
                case MULTI_POINT:
                    write(wkt, (MultiPoint) geometry, dimension);
                    break;
                case LINE_STRING:
                    write(wkt, (LineString) geometry, dimension);
                    break;
                case MULTI_LINE_STRING:
                    write(wkt, (MultiLineString) geometry, dimension);
                    break;
                case POLYGON:
                    write(wkt, (Polygon) geometry, dimension);
                    break;
                case MULTI_SURFACE:
                case COMPOSITE_SURFACE:
                case TRIANGULATED_SURFACE:
                    write(wkt, (SurfaceCollection<?>) geometry, dimension);
                    break;
                case SOLID:
                    write(wkt, (Solid) geometry, dimension);
                    break;
                case COMPOSITE_SOLID:
                case MULTI_SOLID:
                    write(wkt, (SolidCollection<?>) geometry, dimension);
                    break;
                default:
                    throw new GeometryException("Unsupported geometry type '" + geometryType + "'.");
            }

            return wkt.toString();
        } else {
            return null;
        }
    }

    private void write(StringBuilder wkt, Point point, int dimension) {
        wkt.append(WKTConstants.POINT);
        addCoordinateFlag(wkt, dimension);
        addCoordinates(wkt, List.of(point.getCoordinate()), dimension);
    }

    private void write(StringBuilder wkt, MultiPoint multiPoint, int dimension) {
        wkt.append(WKTConstants.MULTIPOINT);
        if (multiPoint.getPoints().isEmpty()) {
            wkt.append(" ").append(WKTConstants.EMPTY);
        } else {
            addCoordinateFlag(wkt, dimension);
            List<Coordinate> coordinates = multiPoint.getPoints().stream()
                    .map(Point::getCoordinate)
                    .collect(Collectors.toList());
            addCoordinates(wkt, coordinates, dimension);
        }
    }

    private void write(StringBuilder wkt, LineString lineString, int dimension) {
        wkt.append(WKTConstants.LINESTRING);
        if (lineString.getPoints().isEmpty()) {
            wkt.append(" ").append(WKTConstants.EMPTY);
        } else {
            addCoordinateFlag(wkt, dimension);
            addCoordinates(wkt, lineString.getPoints(), dimension);
        }
    }

    private void write(StringBuilder wkt, MultiLineString multiLineString, int dimension) {
        wkt.append(WKTConstants.MULTILINESTRING);
        if (multiLineString.getLineStrings().isEmpty()) {
            wkt.append(" ").append(WKTConstants.EMPTY);
        } else {
            addCoordinateFlag(wkt, dimension);
            wkt.append("(");
            List<LineString> lineStrings = multiLineString.getLineStrings();
            for (int i = 0; i < lineStrings.size(); i++) {
                addCoordinates(wkt, lineStrings.get(i).getPoints(), dimension);
                if (i < lineStrings.size() - 1) {
                    wkt.append(", ");
                }
            }

            wkt.append(")");
        }
    }

    private void write(StringBuilder wkt, Polygon polygon, int dimension) {
        wkt.append(WKTConstants.POLYGON);
        if (polygon.getExteriorRing().getPoints().isEmpty()) {
            wkt.append(" ").append(WKTConstants.EMPTY);
        } else {
            addCoordinateFlag(wkt, dimension);
            addPolygon(wkt, polygon, dimension);
        }
    }

    private void write(StringBuilder wkt, SurfaceCollection<?> surfaceCollection, int dimension) {
        wkt.append(WKTConstants.MULTIPOLYGON);
        if (surfaceCollection.getPolygons().isEmpty()) {
            wkt.append(" ").append(WKTConstants.EMPTY);
        } else {
            addCoordinateFlag(wkt, dimension);
            addPolygons(wkt, surfaceCollection.getPolygons(), dimension);
        }
    }

    private void write(StringBuilder wkt, Solid solid, int dimension) {
        wkt.append(WKTConstants.POLYHEDRALSURFACE);
        if (solid.getShell().getPolygons().isEmpty()) {
            wkt.append(" ").append(WKTConstants.EMPTY);
        } else {
            addCoordinateFlag(wkt, dimension);
            addPolygons(wkt, solid.getShell().getPolygons(), dimension);
        }
    }

    private void write(StringBuilder wkt, SolidCollection<?> solidCollection, int dimension) {
        wkt.append(WKTConstants.GEOMETRYCOLLECTION);
        if (solidCollection.getSolids().isEmpty()) {
            wkt.append(" ").append(WKTConstants.EMPTY);
        } else {
            addCoordinateFlag(wkt, dimension);
            wkt.append("(");
            List<Solid> solids = solidCollection.getSolids();
            for (int i = 0; i < solids.size(); i++) {
                write(wkt, solids.get(i), dimension);
                if (i < solids.size() - 1) {
                    wkt.append(", ");
                }
            }

            wkt.append(")");
        }
    }

    private void writeSRID(StringBuilder wkt, Geometry<?> geometry) {
        int srid = geometry.getSRID().orElse(0);
        if (srid > 0) {
            wkt.append(WKTConstants.SRID).append("=").append(srid).append(";");
        }
    }

    private void addCoordinates(StringBuilder wkt, List<Coordinate> coordinates, int dimension) {
        wkt.append("(");
        for (int i = 0; i < coordinates.size(); i++) {
            Coordinate coordinate = coordinates.get(i);
            wkt.append(coordinate.getX()).append(" ").append(coordinate.getY());
            if (dimension == 3) {
                wkt.append(" ").append(coordinate.getZ());
            }
            if (i < coordinates.size() - 1) {
                wkt.append(", ");
            }
        }

        wkt.append(")");
    }

    private void addPolygon(StringBuilder wkt, Polygon polygon, int dimension) {
        wkt.append("(");
        addCoordinates(wkt, polygon.getExteriorRing().getPoints(), dimension);
        if (polygon.hasInteriorRings()) {
            wkt.append(", ");
            List<LinearRing> linearRings = polygon.getInteriorRings();
            for (int i = 0; i < linearRings.size(); i++) {
                addCoordinates(wkt, linearRings.get(i).getPoints(), dimension);
                if (i < linearRings.size() - 1) {
                    wkt.append(", ");
                }
            }
        }

        wkt.append(")");
    }

    private void addPolygons(StringBuilder wkt, List<Polygon> polygons, int dimension) {
        wkt.append("(");
        for (int i = 0; i < polygons.size(); i++) {
            addPolygon(wkt, polygons.get(i), dimension);
            if (i < polygons.size() - 1) {
                wkt.append(", ");
            }
        }

        wkt.append(")");
    }

    private void addCoordinateFlag(StringBuilder wkt, int dimension) {
        wkt.append(dimension == 3 ? " " + WKTConstants.Z + " " : " ");
    }
}
