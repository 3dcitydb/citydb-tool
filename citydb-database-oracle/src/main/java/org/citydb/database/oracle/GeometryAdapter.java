/*
 * citydb-tool - Command-line tool for the 3D City Database
 * https://www.3dcitydb.org/
 *
 * Copyright © 2025, Oracle and/or its affiliates.
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

package org.citydb.database.oracle;

import oracle.spatial.geometry.J3D_Geometry;
import oracle.spatial.geometry.JGeometry;
import org.citydb.database.adapter.DatabaseAdapter;
import org.citydb.database.geometry.GeometryException;
import org.citydb.database.geometry.WKTWriter;
import org.citydb.model.geometry.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class GeometryAdapter extends org.citydb.database.adapter.GeometryAdapter {
    private static final Logger logger = LoggerFactory.getLogger(GeometryAdapter.class);

    private final WKTWriter textWriter = new WKTWriter().includeSRID(true);
    private final SpatialOperationHelper spatialOperationHelper = new SpatialOperationHelper();

    GeometryAdapter(DatabaseAdapter adapter) {
        super(adapter);
    }

    @Override
    public String getAsText(Geometry<?> geometry) throws GeometryException {
        return textWriter.write(geometry);
    }

    @Override
    public boolean hasImplicitGeometries(Connection connection) throws SQLException {
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "select 1 from " +
                             SchemaAdapter.enquoteSqlName(adapter.getConnectionDetails().getSchema()) +
                             ".IMPLICIT_GEOMETRY WHERE ROWNUM < 2")) {
            return rs.next();
        }
    }

    @Override
    public SpatialOperationHelper getSpatialOperationHelper() {
        return spatialOperationHelper;
    }

    @Override
    public Geometry<?> getGeometry(Object geometryObject) throws GeometryException {   //Input geometryObject is from an Object ResultSet.getObject()
        if (geometryObject instanceof Struct struct) {
            try {
                JGeometry oracleGeometry = JGeometry.loadJS(struct);
                logger.debug("Oracle JGeometry type: " + oracleGeometry.getType());
                Geometry citydbGeometry = fromOracleGeometry(oracleGeometry);
                logger.debug("Citydb geometry type: " + citydbGeometry.getGeometryType());
                return citydbGeometry;
            } catch (Exception e) {
                logger.error(e.getMessage(), e);
                throw new GeometryException("Failed to parse geometry.", e);
            }
        } else {
            throw new IllegalArgumentException("Object of type " + geometryObject.getClass().getName() + " is not supported.");
        }
    }

    @Override
    public Struct getGeometry(Geometry<?> geometry, boolean force3D, Connection connection) throws GeometryException {
        try {
            logger.debug("Citydb geometry type: " + geometry.getGeometryType());
            JGeometry oracleGeometry = toOracleGeometry(geometry);
            logger.debug("Oracle JGeometry type: " + oracleGeometry.getType());
            return JGeometry.storeJS(connection.unwrap(oracle.jdbc.OracleConnection.class), oracleGeometry);
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            throw new GeometryException(e);
        }
    }

    @Override
    public Envelope getEnvelope(Object geometryObject) throws GeometryException {
        if (!(geometryObject instanceof Struct struct)) {
            throw new GeometryException("Unsupported geometry object type.");
        }
        try {
            JGeometry geometry = JGeometry.loadJS(struct);
            return getEnvelope(geometry);
        } catch (Exception e) {
            throw new GeometryException("Failed to get envelope.", e);
        }
    }

    private static Envelope getEnvelope(JGeometry geometry) {
        double[] mbr = geometry.getMBR();
        double[] min = Arrays.copyOfRange(mbr, 0, mbr.length / 2);
        double[] max = Arrays.copyOfRange(mbr, mbr.length / 2, mbr.length);
        Coordinate lowerCorner = Coordinate.of(min[0], min[1], min.length == 3 ? min[2] : Double.NaN);
        Coordinate upperCorner = Coordinate.of(max[0], max[1], max.length == 3 ? max[2] : Double.NaN);
        Envelope envelope = Envelope.of(lowerCorner, upperCorner);
        envelope.setSRID(geometry.getSRID());
        return envelope;
    }

    static double[] pointsToCoords(List<Coordinate> points, int dim) {
        int numPoints = points.size();
        double[] coords = new double[points.size() * dim];
        for (int i = 0; i < numPoints; i++) {
            Coordinate point = points.get(i);
            coords[i * dim] = point.getX();
            coords[i * dim + 1] = point.getY();
            coords[i * dim + 2] = point.getZ();
        }
        return coords;
    }

    private static double[] pointToCoords(Coordinate point, int dim) {
        double[] coords = new double[dim];
        coords[0] = point.getX();
        coords[1] = point.getY();
        if (dim >= 3) {
            coords[2] = point.getZ();
        }
        return coords;
    }

    private static String coordinateListToString(List<Coordinate> points) {
        StringBuilder sb = new StringBuilder();
        for (Coordinate point : points) {
            sb.append(point.getX()).append(',').append(point.getY()).append(',').append(point.getZ()).append('\n');
        }
        return sb.toString();
    }

    private static String polygonToString(Polygon polygon) {
        StringBuilder sb = new StringBuilder();
        sb.append("Polygon id ").append(polygon.getObjectId().orElse("null")).append('\n');
        List<LinearRing> rings = polygon.getRings();
        for (int i = 0; i < rings.size(); i++) {
            sb.append("ring ").append(i).append(": ").append(coordinateListToString(rings.get(i).getPoints())).append('\n');
        }
        return sb.toString();
    }

    private static JGeometry polygonToOracleGeometry(Polygon polygon) {
        int dim = polygon.getVertexDimension();
        List<LinearRing> rings = polygon.getRings();
        double[][] polygonCoords = new double[rings.size()][];
        for (int i = 0; i < rings.size(); i++) {
            List<Coordinate> points = rings.get(i).getPoints();
            polygonCoords[i] = pointsToCoords(points, dim);
        }
        return JGeometryUtil.createLinearPolygon(polygonCoords, dim, polygon.getSRID().orElse(0));
    }

    private static JGeometry[] polygonsToOracleGeometries(List<Polygon> polygons) {
        JGeometry[] oraclePolygons = new JGeometry[polygons.size()];
        for (int i = 0; i < oraclePolygons.length; i++) {
            oraclePolygons[i] = polygonToOracleGeometry(polygons.get(i));
        }
        return oraclePolygons;
    }

    private static JGeometry solidToOracleGeometry(Solid solid) {
        List<Polygon> shell = solid.getShell().getPolygons();
        JGeometry[] polygons = new JGeometry[shell.size()];
        for (int i = 0; i < polygons.length; i++) {
            polygons[i] = polygonToOracleGeometry(shell.get(i));
        }
        JGeometry multiPolygon = JGeometryUtil.createSurface(polygons);
        return JGeometryUtil.createSolid(multiPolygon);
    }

    static JGeometry toOracleGeometry(Geometry<?> geometry) {
        GeometryType type = geometry.getGeometryType();
        int dim = geometry.getVertexDimension();
        int srid = geometry.getSRID().orElse(0);

        switch (type) {
            case POINT:
                Coordinate coordinate = ((Point) geometry).getCoordinate();
                double[] pointCoords = {coordinate.getX(), coordinate.getY(), coordinate.getZ()};
                return JGeometry.createPoint(pointCoords, dim, srid);
            case MULTI_POINT:
                List<Point> points = ((MultiPoint) geometry).getPoints();
                double[][] multiPointCoords = new double[points.size()][];
                for (int i = 0; i < points.size(); i++) {
                    Coordinate point = points.get(i).getCoordinate();
                    multiPointCoords[i] = pointToCoords(point, dim);
                }
                return JGeometry.createMultiPoint(multiPointCoords, dim, srid);
            case LINE_STRING:
                double[] lineStringCoords = pointsToCoords(((LineString) geometry).getPoints(), dim);
                return JGeometry.createLinearLineString(lineStringCoords, dim, srid);
            case MULTI_LINE_STRING:
                List<LineString> lineStrings = ((MultiLineString) geometry).getLineStrings();
                double[][] multiLineStringCoords = new double[lineStrings.size()][];
                for (int i = 0; i < lineStrings.size(); i++) {
                    multiLineStringCoords[i] = pointsToCoords(lineStrings.get(i).getPoints(), dim);
                }
                return JGeometry.createLinearMultiLineString(multiLineStringCoords, dim, srid);
            case POLYGON:
                return polygonToOracleGeometry((Polygon) geometry);
            case TRIANGULATED_SURFACE:
                List<Polygon> surfaceTriangles = ((TriangulatedSurface) geometry).getPolygons();
                JGeometry[] oracleSurfaceTriangles = polygonsToOracleGeometries(surfaceTriangles);
                return JGeometryUtil.createSurface(oracleSurfaceTriangles);
            case MULTI_SURFACE:
                JGeometry[] multiSurfacePolygon = polygonsToOracleGeometries(((MultiSurface) geometry).getPolygons());
                return JGeometryUtil.createSurface(multiSurfacePolygon);
            case COMPOSITE_SURFACE:
                List<Polygon> compositeSurface = ((CompositeSurface) geometry).getPolygons();
                JGeometry[] oracleCompositeSurfaces = polygonsToOracleGeometries(compositeSurface);
                return JGeometryUtil.createSurface(oracleCompositeSurfaces);
            case SOLID:
                return solidToOracleGeometry((Solid) geometry);
            case MULTI_SOLID:
                List<Solid> citydbMultiSolids = ((MultiSolid) geometry).getSolids();
                JGeometry[] oracleMultiSolids = new JGeometry[citydbMultiSolids.size()];
                for (int i = 0; i < citydbMultiSolids.size(); i++) {
                    Solid solid = citydbMultiSolids.get(i);
                    JGeometry[] multiSolidPolygon = polygonsToOracleGeometries(solid.getShell().getPolygons());
                    JGeometry surface = JGeometryUtil.createSurface(multiSolidPolygon);
                    oracleMultiSolids[i] = JGeometryUtil.createSolid(surface);
                }
                return JGeometryUtil.createMultiSolid(oracleMultiSolids);
            case COMPOSITE_SOLID:
                List<Solid> citydbCompositeSolids = ((CompositeSolid) geometry).getSolids();
                JGeometry[] oracleCompositeSolids = new JGeometry[citydbCompositeSolids.size()];
                for (int i = 0; i < citydbCompositeSolids.size(); i++) {
                    Solid solid = citydbCompositeSolids.get(i);
                    JGeometry[] compositePolygons = polygonsToOracleGeometries(solid.getShell().getPolygons());
                    JGeometry surface = JGeometryUtil.createSurface(compositePolygons);
                    oracleCompositeSolids[i] = JGeometryUtil.createSolid(surface);
                }
                return JGeometryUtil.createCompositeSolid(oracleCompositeSolids);
            default:
                throw new IllegalArgumentException("Unsupported geometry type: " + type);
        }
    }

    static Geometry<?> fromOracleGeometry(JGeometry oracleGeometry) {
        int gType = oracleGeometry.getType();
        int dim = oracleGeometry.getDimensions();
        int srid = oracleGeometry.getSRID();
        int[] elemInfo = oracleGeometry.getElemInfo();
        double[] ordinates = oracleGeometry.getOrdinatesArray();
        int firstElementType = elemInfo != null && elemInfo.length > 2 ? getElementType(elemInfo[1]) : 0;

        switch (gType) {
            case J3D_Geometry.GTYPE_POINT:
                return createPoint(oracleGeometry.getPoint(), dim).setSRID(srid);
            case J3D_Geometry.GTYPE_MULTIPOINT:
                return createMultiPoint(ordinates, dim).setSRID(srid);
            case JGeometry.GTYPE_CURVE:
                return createLineString(ordinates, dim).setSRID(srid);
            case JGeometry.GTYPE_MULTICURVE:
                return createMultiLineString(elemInfo, ordinates, dim).setSRID(srid);
            case J3D_Geometry.GTYPE_POLYGON:   //same as J3D_Geometry.GTYPE_SURFACE
                if (firstElementType == J3D_Geometry.ETYPE_COMPOSITEPOLYGON) {   //same as J3D_Geometry.ETYPE_COMPOSITESURFACE
                    return createCompositeSurface(elemInfo, ordinates, dim).setSRID(srid);
                } else {
                    return createPolygon(elemInfo, ordinates, dim).setSRID(srid);
                }
            case J3D_Geometry.GTYPE_MULTIPOLYGON:  //same as J3D_Geometry.GTYPE_MULTISURFACE
                // Citydb does not have a MultiPolygon type.
                // Whether the first element type is
                // J3D_Geometry.ETYPE_COMPOSITESURFACE (J3D_Geometry.ETYPE_COMPOSITEPOLYGON)
                // or simple J3D_Geometry.ETYPE_SURFACE (J3D_Geometry.ETYPE_POLYGON),
                // a MultiSurface will be returned.
                return createMultiSurface(elemInfo, ordinates, dim).setSRID(srid);
            case J3D_Geometry.GTYPE_SOLID:
                if (firstElementType == J3D_Geometry.ETYPE_COMPOSITESOLID) {
                    return createCompositeSolid(elemInfo, ordinates, dim).setSRID(srid);
                } else {
                    return createSolid(elemInfo, ordinates, dim).setSRID(srid);
                }
            case J3D_Geometry.GTYPE_MULTISOLID:
                return createMultiSolid(elemInfo, ordinates, dim).setSRID(srid);
            case J3D_Geometry.GTYPE_COLLECTION:
            default:
                throw new IllegalArgumentException("Unsupported JGeometry type: " + gType);
        }
    }

    private static Point createPoint(double[] ordinates, int dim) {
        return Point.of(Coordinate.of(ordinates[0], ordinates[1], dim > 2 ? ordinates[2] : Double.NaN));
    }

    private static MultiPoint createMultiPoint(double[] ordinates, int dim) {
        Point[] points = new Point[ordinates.length / dim];
        for (int i = 0; i < ordinates.length; i += dim) {
            if (dim == 3) {
                points[i / dim] = Point.of(Coordinate.of(ordinates[i], ordinates[i + 1], ordinates[i + 2]));
            } else {
                points[i] = Point.of(Coordinate.of(ordinates[i], ordinates[i + 1]));
            }
        }
        return MultiPoint.of(points);
    }

    private static LineString createLineString(double[] ordinates, int dim) {
        return createLineStringForOneElement(new int[]{1, 2, 1}, 0, 3, ordinates, ordinates.length, dim);
    }

    private static List<Coordinate> extractPoints(int[] elemInfo, int elemInfoStartIndex, int elemInfoEndIndex,
                                                  double[] ordinates, int ordinatesEndIndex,
                                                  int dim) {
        List<Coordinate> points = new ArrayList<>();
        int startIndex = elemInfo != null ? elemInfo[elemInfoStartIndex] : 0;
        int endIndex =
                elemInfo != null && elemInfoStartIndex < elemInfoEndIndex - 3 ?
                        elemInfo[elemInfoStartIndex + 3] :
                        ordinatesEndIndex;
        for (int i = startIndex; i < endIndex; i += dim) {
            Coordinate coordinate = (dim == 3) ?
                    Coordinate.of(ordinates[i - 1], ordinates[i], ordinates[i + 1]) :
                    Coordinate.of(ordinates[i - 1], ordinates[i]);
            points.add(coordinate);
        }
        return points;
    }

    private static LineString createLineStringForOneElement(int[] elemInfo, int elemInfoStartIndex, int elemInfoEndIndex,
                                                            double[] ordinates, int ordinatesEndIndex, int dim) {
        //citygml does not support curve
        if (elemInfo != null && getElementType(elemInfo[elemInfoStartIndex + 1]) != JGeometryUtil.ETYPE_CURVE) {
            throw new IllegalArgumentException("Curve is not supported!");
        }
        List<Coordinate> points = extractPoints(
                elemInfo, elemInfoStartIndex, elemInfoEndIndex,
                ordinates, ordinatesEndIndex, dim);
        if (points.isEmpty()) {
            return null;
        }
        return LineString.of(points);
    }

    private static MultiLineString createMultiLineString(int[] elemInfo,
                                                         double[] ordinates,
                                                         int dim) {
        List<LineString> lineStrings = new ArrayList<>();
        for (int i = 0; i < elemInfo.length; i += 3) {
            int elemInfoEndIndex = elemInfo.length;
            int ordinatesEndIndex = ordinates.length;
            if (i < elemInfo.length - 3) {
                elemInfoEndIndex = i + 3;
                ordinatesEndIndex = elemInfo[elemInfoEndIndex];
            }
            LineString lineString = createLineStringForOneElement(
                    elemInfo, i, elemInfoEndIndex,
                    ordinates, ordinatesEndIndex, dim);
            if (lineString != null) {
                lineStrings.add(lineString);
            }
        }
        return MultiLineString.of(lineStrings);
    }

    private static int getElementType(int eType) {
        return eType % 10;
    }


    private static boolean isInterior(int eType) {
        return eType / 1000 == 2;
    }

    private static LinearRing createLinearRingForOneElement(int[] elemInfo, int elemInfoStartIndex, int elemInfoEndIndex,
                                                            double[] ordinates, int ordinatesEndIndex, int dim) {
        if (getElementType(elemInfo[elemInfoStartIndex + 1]) != J3D_Geometry.ETYPE_POLYGON) {
            return null;
        }
        List<Coordinate> points = extractPoints(
                elemInfo, elemInfoStartIndex, elemInfoEndIndex,
                ordinates, ordinatesEndIndex, dim);
        if (points.isEmpty()) {
            return null;
        }
        return LinearRing.of(points);
    }

    private static Polygon createPolygon(int[] elemInfo, double[] ordinates, int dim) {  //polygon could have holes/interior rings
        LinearRing exteriorRing = null;
        List<LinearRing> interiorRings = new ArrayList<>();
        for (int i = 0; i < elemInfo.length; i += 3) {
            boolean isInterior = isInterior(elemInfo[i + 1]);
            int elemInfoEndIndex = elemInfo.length;
            int ordinatesEndIndex = ordinates.length;
            if (i < elemInfo.length - 3) {
                elemInfoEndIndex = i + 3;
                ordinatesEndIndex = elemInfo[elemInfoEndIndex];
            }
            LinearRing currRing = createLinearRingForOneElement(
                    elemInfo, i, elemInfoEndIndex,
                    ordinates, ordinatesEndIndex, dim);
            if (currRing == null)
                continue;
            if (exteriorRing == null) {
                assert (!isInterior);
                exteriorRing = currRing;
            } else {
                assert (isInterior);
                interiorRings.add(currRing);
            }
        }
        return Polygon.of(exteriorRing, interiorRings);
    }

    private static List<Polygon> createMultiPolygon(int[] elemInfo, int elemInfoStartIndex, int elemInfoEndIndex,
                                                    double[] ordinates, int ordinatesEndIndex, int dim) {
        if (elemInfo == null || elemInfo.length < 3) {
            return null;
        }
        List<Polygon> polygons = new ArrayList<>();
        LinearRing exteriorRing = null;
        List<LinearRing> interiorRings = new ArrayList<>();
        for (int i = elemInfoStartIndex; i < elemInfoEndIndex; i += 3) {
            if (getElementType(elemInfo[i + 1]) != J3D_Geometry.ETYPE_POLYGON) {
                continue;
            }
            boolean isInterior = isInterior(elemInfo[i + 1]);
            int currElemInfoEndIndex = elemInfoEndIndex;
            int currPolygonOrdinatesEndIndex = ordinatesEndIndex;
            if (i < elemInfoEndIndex - 3) {
                currElemInfoEndIndex = i + 3;
                currPolygonOrdinatesEndIndex = elemInfo[currElemInfoEndIndex];
            }
            LinearRing currRing = createLinearRingForOneElement(
                    elemInfo, i, currElemInfoEndIndex, ordinates, currPolygonOrdinatesEndIndex, dim);
            if (currRing == null) {
                continue;
            }
            if (!isInterior) {
                //wrap up the previous polygon
                if (exteriorRing != null) {
                    polygons.add(Polygon.of(exteriorRing, interiorRings));
                }
                exteriorRing = currRing;
                interiorRings = new ArrayList<>();
            } else {
                interiorRings.add(currRing);
            }
        }
        //wrap up the last polygon
        if (exteriorRing != null) {
            polygons.add(Polygon.of(exteriorRing, interiorRings));
        }
        return polygons;
    }

    //Note: MultiSurface does not support multiple SurfaceMembers, each with multiple Polygons.
    private static MultiSurface createMultiSurface(int[] elemInfo, double[] ordinates, int dim) {
        List<Polygon> polygons = createMultiPolygon(
                elemInfo, 0, elemInfo.length, ordinates, ordinates.length, dim);
        return MultiSurface.of(polygons);
    }

    private static CompositeSurface createCompositeSurface(int[] elemInfo, double[] ordinates, int dim) {
        List<Polygon> polygons = createMultiPolygon(
                elemInfo, 0, elemInfo.length, ordinates, ordinates.length, dim);
        return CompositeSurface.of(polygons);
    }

    private static Solid createSolid(int[] elemInfo,
                                     double[] ordinates,
                                     int dim) {
        return createSingleSolid(elemInfo, 0, elemInfo.length, ordinates, ordinates.length, dim);
    }

    private static List<Polygon> createRectangularBox(int[] elemInfo, int elemInfoStartIndex, double[] ordinates, int dim) {
        List<Polygon> polygons = new ArrayList<>();
        int ordinateStartIndex = elemInfo[elemInfoStartIndex];
        Envelope envelope = Envelope.of(
                Coordinate.of(
                        ordinates[ordinateStartIndex],
                        ordinates[ordinateStartIndex + 1],
                        ordinates[ordinateStartIndex + 2]),
                Coordinate.of(
                        ordinates[ordinateStartIndex + 3],
                        ordinates[ordinateStartIndex + 4],
                        ordinates[ordinateStartIndex + 5])
        );
        polygons.add(envelope.convertToPolygon());
        return polygons;
    }

    private static Solid createSingleSolid(int[] elemInfo, int elemInfoStartIndex, int elemInfoEndIndex,
                                           double[] ordinates, int ordinatesEndIndex,
                                           int dim) {
        //need to handle optimized representation for rectangular boxes
        if (getElementType(elemInfo[elemInfoStartIndex + 1]) != J3D_Geometry.ETYPE_SOLID) {
            throw new IllegalArgumentException("The first element triplet must be of solid element type! " + Arrays.toString(elemInfo));
        }
        List<Polygon> polygons = null;
        if (elemInfo[elemInfoStartIndex + 2] == JGeometryUtil.EITPR_RECTANGLE) {
            polygons = createRectangularBox(elemInfo, elemInfoStartIndex, ordinates, dim);
        } else {
            polygons = createMultiPolygon(elemInfo, elemInfoStartIndex, elemInfoEndIndex, ordinates, ordinatesEndIndex, dim);
        }
        CompositeSurface shell = CompositeSurface.of(polygons);
        return Solid.of(shell);
    }

    private static List<Solid> createSolids(int[] elemInfo, double[] ordinates, int dim) {
        List<Solid> solids = new ArrayList<>();
        ArrayList<Integer> elemInfoStartIndexes = new ArrayList<>();
        for (int i = 0; i < elemInfo.length; i += 3) {
            if (getElementType(elemInfo[i + 1]) == J3D_Geometry.ETYPE_SOLID) {
                //citydb does not support solid with voids
                if (isInterior(elemInfo[i])) {
                    continue;
                }
                elemInfoStartIndexes.add(i);
            }
        }
        int numSolids = elemInfoStartIndexes.size();
        for (int i = 0; i < numSolids; i++) {
            int elemInfoStartIndex = elemInfoStartIndexes.get(i);
            int elemInfoEndIndex = elemInfo.length;
            int ordinatesEndIndex = ordinates.length;
            if (i < numSolids - 1) {
                elemInfoEndIndex = elemInfoStartIndexes.get(i + 1);
                ordinatesEndIndex = elemInfo[elemInfoEndIndex];
            }
            Solid solid = createSingleSolid(elemInfo, elemInfoStartIndex, elemInfoEndIndex, ordinates, ordinatesEndIndex, dim);
            if (solid != null) {
                solids.add(solid);
            }
        }
        return solids;
    }

    private static MultiSolid createMultiSolid(int[] elemInfo, double[] ordinates, int dim) {
        List<Solid> solids = createSolids(elemInfo, ordinates, dim);
        return MultiSolid.of(solids);
    }

    private static CompositeSolid createCompositeSolid(int[] elemInfo, double[] ordinates, int dim) {
        List<Solid> solids = createSolids(elemInfo, ordinates, dim);
        return CompositeSolid.of(solids);
    }

}
