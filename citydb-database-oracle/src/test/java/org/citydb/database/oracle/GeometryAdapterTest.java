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
import org.citydb.model.geometry.CompositeSolid;
import org.citydb.model.geometry.CompositeSurface;
import org.citydb.model.geometry.Coordinate;
import org.citydb.model.geometry.LineString;
import org.citydb.model.geometry.LinearRing;
import org.citydb.model.geometry.MultiLineString;
import org.citydb.model.geometry.MultiPoint;
import org.citydb.model.geometry.MultiSolid;
import org.citydb.model.geometry.MultiSurface;
import org.citydb.model.geometry.Point;
import org.citydb.model.geometry.Polygon;
import org.citydb.model.geometry.Solid;

import java.util.Arrays;
import java.util.List;

public class GeometryAdapterTest {

    private static int srid = 0;

    private static String pointToString(Point geometry) {
        StringBuilder sb = new StringBuilder();
        sb.append("OBJECT ID: ").append(geometry.getObjectId().orElse("NULL")).append('\n')
                .append("Geometry type: ").append(geometry.getGeometryType()).append('\n')
                .append("Dimension: ").append(geometry.getVertexDimension()).append('\n')
                .append("SRID: ").append(geometry.getSRID().orElse(-1)).append('\n');
        Coordinate coordinate = geometry.getCoordinate();
        sb.append(Arrays.toString(new double[]{coordinate.getX(), coordinate.getY(), coordinate.getZ()}));
        return sb.toString();
    }

    private static String multiPointToString(MultiPoint geometry) {
        StringBuilder sb = new StringBuilder();
        sb.append("OBJECT ID: ").append(geometry.getObjectId().orElse("NULL")).append('\n')
                .append("Geometry type: ").append(geometry.getGeometryType()).append('\n')
                .append("Dimension: ").append(geometry.getVertexDimension()).append('\n')
                .append("SRID: ").append(geometry.getSRID().orElse(-1)).append('\n');
        List<Point> points = geometry.getPoints();
        for(int i=0; i<points.size(); i++) {
            Coordinate coordinate = points.get(i).getCoordinate();
            sb.append("Point ").append(i).append(":\n")
                    .append(Arrays.toString(
                            new double[]{coordinate.getX(), coordinate.getY(), coordinate.getZ()}));
        }
        return sb.toString();
    }

    private static String lineStringToString(LineString geometry) {
        StringBuilder sb = new StringBuilder();
        sb.append("OBJECT ID: ").append(geometry.getObjectId().orElse("NULL")).append('\n')
                .append("Geometry type: ").append(geometry.getGeometryType()).append('\n')
                .append("Dimension: ").append(geometry.getVertexDimension()).append('\n')
                .append("SRID: ").append(geometry.getSRID().orElse(-1)).append('\n');
            sb.append(Arrays.toString(GeometryAdapter.pointsToCoords(geometry.getPoints(), geometry.getVertexDimension())));
        return sb.toString();
    }

    private static String multiLineStringToString(MultiLineString geometry) {
        StringBuilder sb = new StringBuilder();
        sb.append("OBJECT ID: ").append(geometry.getObjectId().orElse("NULL")).append('\n')
                .append("Geometry type: ").append(geometry.getGeometryType()).append('\n')
                .append("Dimension: ").append(geometry.getVertexDimension()).append('\n')
                .append("SRID: ").append(geometry.getSRID().orElse(-1)).append('\n');
        List<LineString> lineStrings = geometry.getLineStrings();
        for(int i=0; i<lineStrings.size(); i++) {
            sb.append("LineString ").append(i).append(":\n")
                    .append(Arrays.toString(GeometryAdapter.pointsToCoords(lineStrings.get(i).getPoints(), geometry.getVertexDimension()))).append('\n');
        }
        return sb.toString();
    }

    private static String polygonToString(Polygon geometry) {
        StringBuilder sb = new StringBuilder();
        sb.append("OBJECT ID: ").append(geometry.getObjectId().orElse("NULL")).append('\n')
                .append("Geometry type: ").append(geometry.getGeometryType()).append('\n')
                .append("Dimension: ").append(geometry.getVertexDimension()).append('\n')
                .append("SRID: ").append(geometry.getSRID().orElse(-1)).append('\n');
        List<LinearRing> rings = geometry.getRings();
        for(int i=0; i<rings.size(); i++) {
            sb.append("Ring ").append(i).append(":\n")
                    .append(Arrays.toString(GeometryAdapter.pointsToCoords(rings.get(i).getPoints(), geometry.getVertexDimension()))).append('\n');
        }
        return sb.toString();
    }

    private static String multiSurfaceToString(MultiSurface geometry) {
        StringBuilder sb = new StringBuilder();
        sb.append("OBJECT ID: ").append(geometry.getObjectId().orElse("NULL")).append('\n')
                .append("Geometry type: ").append(geometry.getGeometryType()).append('\n')
                .append("Dimension: ").append(geometry.getVertexDimension()).append('\n')
                .append("SRID: ").append(geometry.getSRID().orElse(-1)).append('\n');
        List<Polygon> polygons = geometry.getPolygons();
        for(int i=0; i<polygons.size(); i++) {
            sb.append("Polygon ").append(i).append(":\n").append(polygonToString(polygons.get(i))).append('\n');
        }
        return sb.toString();
    }

    private static String compositeSurfaceToString(CompositeSurface geometry) {
        StringBuilder sb = new StringBuilder();
        sb.append("OBJECT ID: ").append(geometry.getObjectId().orElse("NULL")).append('\n')
                .append("Geometry type: ").append(geometry.getGeometryType()).append('\n')
                .append("Dimension: ").append(geometry.getVertexDimension()).append('\n')
                .append("SRID: ").append(geometry.getSRID().orElse(-1)).append('\n');
        List<Polygon> polygons = geometry.getPolygons();
        for(int i=0; i<polygons.size(); i++) {
            sb.append("Polygon ").append(i).append(":\n").append(polygonToString(polygons.get(i))).append('\n');
        }
        return sb.toString();
    }

    private static String solidToString(Solid geometry) {
        StringBuilder sb = new StringBuilder();
        sb.append("OBJECT ID: ").append(geometry.getObjectId().orElse("NULL")).append('\n')
                .append("Geometry type: ").append(geometry.getGeometryType()).append('\n')
                .append("Dimension: ").append(geometry.getVertexDimension()).append('\n')
                .append("SRID: ").append(geometry.getSRID().orElse(-1)).append('\n');
        CompositeSurface compositeSurface = geometry.getShell();
        sb.append("Composite Surface: ").append(compositeSurfaceToString(compositeSurface));
        return sb.toString();
    }

    private static String multiSolidToString(MultiSolid geometry) {
        StringBuilder sb = new StringBuilder();
        sb.append("OBJECT ID: ").append(geometry.getObjectId().orElse("NULL")).append('\n')
                .append("Geometry type: ").append(geometry.getGeometryType()).append('\n')
                .append("Dimension: ").append(geometry.getVertexDimension()).append('\n')
                .append("SRID: ").append(geometry.getSRID().orElse(-1)).append('\n');
        List<Solid> solids = geometry.getSolids();
        for(int i=0; i<solids.size(); i++) {
            sb.append("Solid ").append(i).append(":\n").append(solidToString(solids.get(i))).append('\n');
        }
        return sb.toString();
    }

    private static String compositeSolidToString(CompositeSolid geometry) {
        StringBuilder sb = new StringBuilder();
        sb.append("OBJECT ID: ").append(geometry.getObjectId().orElse("NULL")).append('\n')
                .append("Geometry type: ").append(geometry.getGeometryType()).append('\n')
                .append("Dimension: ").append(geometry.getVertexDimension()).append('\n')
                .append("SRID: ").append(geometry.getSRID().orElse(-1)).append('\n');
        List<Solid> solids = geometry.getSolids();
        for(int i=0; i<solids.size(); i++) {
            sb.append("Solid ").append(i).append(":\n").append(solidToString(solids.get(i))).append('\n');
        }
        return sb.toString();
    }

    public void testOracleToCitydbPoint() {
        JGeometry oracleGeometry = new JGeometry(1, 2, 3, srid);
        Point citydbGeometry = (Point)GeometryAdapter.fromOracleGeometry(oracleGeometry);
        System.out.println("==========Point");
        System.out.println(pointToString(citydbGeometry));
    }

    public void testOracleToCitydbMultiPoint() {
        JGeometry oracleGeometry =
                new JGeometry(
                        JGeometryUtil.getSDOGType(J3D_Geometry.GTYPE_MULTIPOINT, 3), srid,
                        new int[]{1,1,1, 4,1,1},
                        new double[]{1,1,1, 0,0,0});
        MultiPoint citydbGeometry = (MultiPoint) GeometryAdapter.fromOracleGeometry(oracleGeometry);
        System.out.println("==========MultiPoint");
        System.out.println(multiPointToString(citydbGeometry));
    }

    public void testOracleToCitydbLinearString() {
        JGeometry oracleGeometry =
                new JGeometry(
                        JGeometryUtil.getSDOGType(J3D_Geometry.GTYPE_CURVE, 3), srid,
                        new int[]{1,2,1},
                        new double[]{1,1,1, 0,0,0});
        LineString citydbGeometry = (LineString)GeometryAdapter.fromOracleGeometry(oracleGeometry);
        System.out.println("==========LinearString");
        System.out.println(lineStringToString(citydbGeometry));
    }

    public void testOracleToCitydbMultiLinearString() {
        JGeometry oracleGeometry =
                new JGeometry(
                        JGeometryUtil.getSDOGType(J3D_Geometry.GTYPE_MULTICURVE, 3), srid,
                        new int[]{1, 2, 1, 7, 2, 1},
                        new double[]{1,1,1, 0,0,0, 1,0,1, 0,1,0});
        MultiLineString citydbGeometry = (MultiLineString)GeometryAdapter.fromOracleGeometry(oracleGeometry);
        System.out.println("==========MultiLinearString");
        System.out.println(multiLineStringToString(citydbGeometry));
    }

    public void testOracleToCitydbPolygon() {
        JGeometry oracleGeometry =
                new JGeometry(
                        JGeometryUtil.getSDOGType(J3D_Geometry.GTYPE_POLYGON, 3), srid,
                        new int[]{1,1003,1},
                        new double[]{
                                0.5,0.0,0.0,
                                0.5,1.0,0.0,
                                0.0,1.0,1.0,
                                0.0,0.0,1.0,
                                0.5,0.0,0.0
                        });
        Polygon geometry = (Polygon)GeometryAdapter.fromOracleGeometry(oracleGeometry);
        System.out.println("==========Polygon");
        System.out.println(polygonToString(geometry));
    }

    public void testOracleToCitydbMultiPolygon() {
        JGeometry oracleGeometry =
                new JGeometry(
                        JGeometryUtil.getSDOGType(J3D_Geometry.GTYPE_MULTIPOLYGON, 3), srid,
                        new int[]{1,1003,1,16,1003,1},
                        new double[]{
                                6.0,6.0,6.0,
                                5.0,6.0,10.0,
                                3.0,4.0,8.0,
                                4.0,4.0,4.0,
                                6.0,6.0,6.0,
                                0.5,0.0,0.0,
                                0.5,1.0,0.0,
                                0.0,1.0,1.0,
                                0.0,0.0,1.0,
                                0.5,0.0,0.0
                        });
        //Citydb does not have multi-polygon. Use multi-surface instead.
        MultiSurface geometry = (MultiSurface)GeometryAdapter.fromOracleGeometry(oracleGeometry);
        System.out.println("==========MultiPolygon");
        System.out.println(multiSurfaceToString(geometry));
    }

    public void testOracleToCitydbPolygonWithHole() {
        JGeometry oracleGeometry =
                new JGeometry(
                        JGeometryUtil.getSDOGType(J3D_Geometry.GTYPE_POLYGON, 3), srid,
                        new int[]{1,1003,1,16,2003,1},
                        new double[]{
                                0.5,0.0,0.0,
                                0.5,1.0,0.0,
                                0.0,1.0,1.0,
                                0.0,0.0,1.0,
                                0.5,0.0,0.0,
                                0.25,0.5,0.5,
                                0.15,0.5,0.7,
                                0.15,0.6,0.7,
                                0.25,0.6,0.5,
                                0.25,0.5,0.5
                        });
        Polygon geometry = (Polygon)GeometryAdapter.fromOracleGeometry(oracleGeometry);
        System.out.println("==========PolygonWithHole");
        System.out.println(polygonToString(geometry));
    }

    public void testOracleToCitydbCompositeSurface() {
        JGeometry oracleGeometry =
                new JGeometry(
                        JGeometryUtil.getSDOGType(J3D_Geometry.GTYPE_SURFACE, 3), srid,
                        new int[]{1,1006,2,1,1003,1,16,1003,1},
                        new double[]{
                                0.5,0.0,0.0,
                                0.5,1.0,0.0,
                                0.0,1.0,0.0,
                                0.0,0.0,0.0,
                                0.5,0.0,0.0,
                                1.5,0.0,0.0,
                                2.5,1.0,0.0,
                                1.5,2.0,0.0,
                                0.5,2.0,0.0,
                                0.5,0.0,0.0,
                                1.5,0.0,0.0
                        });
        CompositeSurface citydbGeometry = (CompositeSurface) GeometryAdapter.fromOracleGeometry(oracleGeometry);
        System.out.println("==========CompositeSurface");
        System.out.println(compositeSurfaceToString(citydbGeometry));
    }

    public void testOracleToCitydbSurfaceWithHole() {
        JGeometry oracleGeometry =
                new JGeometry(
                        JGeometryUtil.getSDOGType(J3D_Geometry.GTYPE_SURFACE, 3), srid,
                        new int[]{1,1006,2,1,1003,1,16,2003,1,31,1003,1},
                        new double[]{
                                0.5,0.0,0.0,
                                0.5,1.0,0.0,
                                0.0,1.0,1.0,
                                0.0,0.0,1.0,
                                0.5,0.0,0.0,

                                0.25,0.5,0.5,
                                0.15,0.5,0.7,
                                0.15,0.6,0.7,
                                0.25,0.6,0.5,
                                0.25,0.5,0.5,
                                1.5,0.0,0.0,
                                2.5,1.0,0.0,
                                1.5,2.0,0.0,
                                0.5,2.0,0.0,
                                0.5,0.0,0.0,
                                1.5,0.0,0.0
                        });
        CompositeSurface citydbGeometry = (CompositeSurface) GeometryAdapter.fromOracleGeometry(oracleGeometry);
        System.out.println("==========SurfaceWithHole");
        System.out.println(compositeSurfaceToString(citydbGeometry));
    }

    public void testOracleToCitydbMultiSurface() {
        JGeometry oracleGeometry =
                new JGeometry(
                        JGeometryUtil.getSDOGType(J3D_Geometry.GTYPE_MULTISURFACE, 3), srid,   //same as GTYPE_MULTIPOLYGON
                        new int[]{1,1006,1,1,1003,1,16,1006,1,16,1003,1,31,1003,1,46,2003,1},
                        new double[]{
                                6.0,6.0,6.0,
                                5.0,6.0,10.0,
                                3.0,4.0,8.0,
                                4.0,4.0,4.0,
                                6.0,6.0,6.0,

                                0.5,0.0,0.0,
                                0.5,1.0,0.0,
                                0.0,1.0,1.0,
                                0.0,0.0,1.0,
                                0.5,0.0,0.0,

                                0.5,0.0,0.0,
                                0.5,1.0,0.0,
                                0.0,1.0,1.0,
                                0.0,0.0,1.0,
                                0.5,0.0,0.0,
                                0.25,0.5,0.5,
                                0.15,0.5,0.7,
                                0.15,0.6,0.7,
                                0.25,0.6,0.5,
                                0.25,0.5,0.5
                        });
        MultiSurface geometry = (MultiSurface)GeometryAdapter.fromOracleGeometry(oracleGeometry);
        System.out.println("==========MultiSurface");
        System.out.println(multiSurfaceToString(geometry));
    }

    public void testOracleToCitydbSolid() {
        JGeometry oracleGeometry =
                new JGeometry(
                        JGeometryUtil.getSDOGType(J3D_Geometry.GTYPE_SOLID, 3), srid,
                        new int[]{1,1007,1,1,1006,6,1,1003,1,16,1003,1,31,1003,1,46,1003,1,61,1003,1,76,1003,1},
                        new double[]{
                                1.0,0.0,-1.0,
                                1.0,1.0,-1.0,
                                1.0,1.0,1.0,
                                1.0,0.0,1.0,
                                1.0,0.0,-1.0,
                                1.0,0.0,1.0,
                                0.0,0.0,1.0,
                                0.0,0.0,-1.0,
                                1.0,0.0,-1.0,
                                1.0,0.0,1.0,
                                0.0,1.0,1.0,
                                0.0,1.0,-1.0,
                                0.0,0.0,-1.0,
                                0.0,0.0,1.0,
                                0.0,1.0,1.0,
                                1.0,1.0,-1.0,
                                0.0,1.0,-1.0,
                                0.0,1.0,1.0,
                                1.0,1.0,1.0,
                                1.0,1.0,-1.0,
                                1.0,1.0,1.0,
                                0.0,1.0,1.0,
                                0.0,0.0,1.0,
                                1.0,0.0,1.0,
                                1.0,1.0,1.0,
                                1.0,1.0,-1.0,
                                1.0,0.0,-1.0,
                                0.0,0.0,-1.0,
                                0.0,1.0,-1.0,
                                1.0,1.0,-1.0
                        });
        Solid geometry = (Solid)GeometryAdapter.fromOracleGeometry(oracleGeometry);
        System.out.println("==========Solid");
        System.out.println(solidToString(geometry));
    }

    public void testOracleToCitydbCompositeSolid() {
        JGeometry oracleGeometry =
                new JGeometry(
                        JGeometryUtil.getSDOGType(J3D_Geometry.GTYPE_SOLID, 3), srid,
                        new int[]{1,1008,2,1,1007,1,1,1006,6,1,1003,1,16,1003,1,31,1003,1,46,1003,1,61,1003,1,76,1003,1,91,1007,1,91,1006,7,91,1003,1,106,1003,1,121,1003,1,136,1003,1,151,1003,1,166,1003,1,184,1003,1},
                        new double[]{
                                -2.0,1.0,3.0,
                                -2.0,1.0,0.0,
                                -3.0,1.0,0.0,
                                -3.0,1.0,3.0,
                                -2.0,1.0,3.0,
                                -3.0,1.0,3.0,
                                -3.0,1.0,0.0,
                                -3.0,-1.0,0.0,
                                -3.0,-1.0,3.0,
                                -3.0,1.0,3.0,
                                -3.0,-1.0,3.0,
                                -3.0,-1.0,0.0,
                                -2.0,-1.0,0.0,
                                -2.0,-1.0,3.0,
                                -3.0,-1.0,3.0,
                                -2.0,-1.0,3.0,
                                -2.0,-1.0,0.0,
                                -2.0,1.0,0.0,
                                -2.0,1.0,3.0,
                                -2.0,-1.0,3.0,
                                -2.0,-1.0,3.0,
                                -2.0,1.0,3.0,
                                -3.0,1.0,3.0,
                                -3.0,-1.0,3.0,
                                -2.0,-1.0,3.0,
                                -2.0,1.0,0.0,
                                -2.0,-1.0,0.0,
                                -3.0,-1.0,0.0,
                                -3.0,1.0,0.0,
                                -2.0,1.0,0.0,
                                2.0,0.0,3.0,
                                2.0,0.0,0.0,
                                4.0,2.0,0.0,
                                4.0,2.0,3.0,
                                2.0,0.0,3.0,
                                4.5,-2.0,3.0,
                                4.5,-2.0,0.0,
                                2.0,0.0,0.0,
                                2.0,0.0,3.0,
                                4.5,-2.0,3.0,
                                4.5,-2.0,3.0,
                                -2.0,-2.0,3.0,
                                -2.0,-2.0,0.0,
                                4.5,-2.0,0.0,
                                4.5,-2.0,3.0,
                                -2.0,-2.0,3.0,
                                -2.0,2.0,3.0,
                                -2.0,2.0,0.0,
                                -2.0,-2.0,0.0,
                                -2.0,-2.0,3.0,
                                4.0,2.0,3.0,
                                4.0,2.0,0.0,
                                -2.0,2.0,0.0,
                                -2.0,2.0,3.0,
                                4.0,2.0,3.0,
                                2.0,0.0,3.0,
                                4.0,2.0,3.0,
                                -2.0,2.0,3.0,
                                -2.0,-2.0,3.0,
                                4.5,-2.0,3.0,
                                2.0,0.0,3.0,
                                2.0,0.0,0.0,
                                4.5,-2.0,0.0,
                                -2.0,-2.0,0.0,
                                -2.0,2.0,0.0,
                                4.0,2.0,0.0,
                                2.0,0.0,0.0
                        });
        CompositeSolid geometry = (CompositeSolid)GeometryAdapter.fromOracleGeometry(oracleGeometry);
        System.out.println("==========CompositeSolid");
        System.out.println(compositeSolidToString(geometry));
    }

    public void testOracleToCitydbMultiSolid() {
        JGeometry oracleGeometry =
                new JGeometry(
                        JGeometryUtil.getSDOGType(J3D_Geometry.GTYPE_MULTISOLID, 3), srid,
                        new int[]{1, 1007, 1, 1, 1006, 6, 1, 1003, 1, 16, 1003, 1, 31, 1003, 1, 46, 1003, 1, 61, 1003, 1, 76, 1003, 1, 91, 1007, 1, 91, 1006, 7, 91, 1003, 1, 106, 1003, 1, 121, 1003, 1, 136, 1003, 1, 151, 1003, 1, 166, 1003, 1, 184, 1003, 1},
                        new double[]{
                                1.0, 0.0, 4.0,
                                1.0, 1.0, 4.0,
                                1.0, 1.0, 6.0,
                                1.0, 0.0, 6.0,
                                1.0, 0.0, 4.0,
                                1.0, 0.0, 6.0,
                                0.0, 0.0, 6.0,
                                0.0, 0.0, 4.0,
                                1.0, 0.0, 4.0,
                                1.0, 0.0, 6.0,
                                0.0, 1.0, 6.0,
                                0.0, 1.0, 4.0,
                                0.0, 0.0, 4.0,
                                0.0, 0.0, 6.0,
                                0.0, 1.0, 6.0,
                                1.0, 1.0, 4.0,
                                0.0, 1.0, 4.0,
                                0.0, 1.0, 6.0,
                                1.0, 1.0, 6.0,
                                1.0, 1.0, 4.0,
                                1.0, 1.0, 6.0,
                                0.0, 1.0, 6.0,
                                0.0, 0.0, 6.0,
                                1.0, 0.0, 6.0,
                                1.0, 1.0, 6.0,
                                1.0, 1.0, 4.0,
                                1.0, 0.0, 4.0,
                                0.0, 0.0, 4.0,
                                0.0, 1.0, 4.0,
                                1.0, 1.0, 4.0,
                                2.0, 0.0, 3.0,
                                2.0, 0.0, 0.0,
                                4.0, 2.0, 0.0,
                                4.0, 2.0, 3.0,
                                2.0, 0.0, 3.0,
                                4.5, -2.0, 3.0,
                                4.5, -2.0, 0.0,
                                2.0, 0.0, 0.0,
                                2.0, 0.0, 3.0,
                                4.5, -2.0, 3.0,
                                4.5, -2.0, 3.0,
                                -2.0, -2.0, 3.0,
                                -2.0, -2.0, 0.0,
                                4.5, -2.0, 0.0,
                                4.5, -2.0, 3.0,
                                -2.0, -2.0, 3.0,
                                -2.0, 2.0, 3.0,
                                -2.0, 2.0, 0.0,
                                -2.0, -2.0, 0.0,
                                -2.0, -2.0, 3.0,
                                4.0, 2.0, 3.0,
                                4.0, 2.0, 0.0,
                                -2.0, 2.0, 0.0,
                                -2.0, 2.0, 3.0,
                                4.0, 2.0, 3.0,
                                2.0, 0.0, 3.0,
                                4.0, 2.0, 3.0,
                                -2.0, 2.0, 3.0,
                                -2.0, -2.0, 3.0,
                                4.5, -2.0, 3.0,
                                2.0, 0.0, 3.0,
                                2.0, 0.0, 0.0,
                                4.5, -2.0, 0.0,
                                -2.0, -2.0, 0.0,
                                -2.0, 2.0, 0.0,
                                4.0, 2.0, 0.0,
                                2.0, 0.0, 0.0
                        });
        MultiSolid citydbGeometry = (MultiSolid) GeometryAdapter.fromOracleGeometry(oracleGeometry);
        System.out.println("==========MultiSolid");
        System.out.println(multiSolidToString(citydbGeometry));
    }

    public void testCitydbToOraclePoint() {
        Point citydbGeometry =
                Point.of(
                        Coordinate.of(1, 2, 3)
                ).setSRID(srid);
        JGeometry oracleGeometry = GeometryAdapter.toOracleGeometry(citydbGeometry);
        System.out.println("==========Point");
        System.out.println(oracleGeometry.toStringFull());
    }

    public void testCitydbToOracleMultiPoint() {
        MultiPoint citydbGeometry =
                MultiPoint.of(
                        new Point[]{
                                Point.of(
                                        Coordinate.of(1, 1, 1)
                                ),
                                Point.of(
                                        Coordinate.of(0, 0, 0)
                                )
                        }
                ).setSRID(srid);
        JGeometry oracleGeometry = GeometryAdapter.toOracleGeometry(citydbGeometry);
        System.out.println("==========MultiPoint");
        System.out.println(oracleGeometry.toStringFull());
    }

    public void testCitydbToOracleLinearString() {
        LineString citydbGeometry =
                LineString.of(
                        new Coordinate[]{
                                Coordinate.of(1,1,1),
                                Coordinate.of(0,0,0)
                        }
                ).setSRID(srid);
        JGeometry oracleGeometry = GeometryAdapter.toOracleGeometry(citydbGeometry);
        System.out.println("==========LinearString");
        System.out.println(oracleGeometry.toStringFull());
    }

    public void testCitydbToOracleMultiLinearString() {
        MultiLineString citydbGeometry =
                MultiLineString.of(
                        new LineString[]{
                                LineString.of(
                                        new Coordinate[]{
                                                Coordinate.of(1,1,1),
                                                Coordinate.of(0,0,0)
                                        }
                                ),
                                LineString.of(
                                        new Coordinate[]{
                                                Coordinate.of(1,0,1),
                                                Coordinate.of(0,1,0)
                                        }
                                )
                        }
                ).setSRID(srid);
        JGeometry oracleGeometry = GeometryAdapter.toOracleGeometry(citydbGeometry);
        System.out.println("==========MultiLinearString");
        System.out.println(oracleGeometry.toStringFull());
    }

    public void testCitydbToOraclePolygon() {
        Polygon citydbGeometry =
                Polygon.of(
                        LinearRing.of(
                                new Coordinate[]{
                                        Coordinate.of(0.5,0.0,0.0),
                                        Coordinate.of(0.5,1.0,0.0),
                                        Coordinate.of(0.0,1.0,1.0),
                                        Coordinate.of(0.0,0.0,1.0),
                                        Coordinate.of(0.5,0.0,0.0)
                                }
                        )
                ).setSRID(srid);
        JGeometry oracleGeometry = GeometryAdapter.toOracleGeometry(citydbGeometry);
        System.out.println("==========Polygon");
        System.out.println(oracleGeometry.toStringFull());
    }

    public void testCitydbToOracleMultiPolygon() {
        MultiSurface citydbGeometry =
                MultiSurface.of(
                        Polygon.of(
                                LinearRing.of(
                                        new Coordinate[]{
                                                Coordinate.of(6.0,6.0,6.0),
                                                Coordinate.of(5.0,6.0,10.0),
                                                Coordinate.of(3.0,4.0,8.0),
                                                Coordinate.of(4.0,4.0,4.0),
                                                Coordinate.of(6.0,6.0,6.0)
                                        }
                                )
                        ),
                        Polygon.of(
                                LinearRing.of(
                                        new Coordinate[]{
                                                Coordinate.of(0.5,0.0,0.0),
                                                Coordinate.of(0.5,1.0,0.0),
                                                Coordinate.of(0.0,1.0,1.0),
                                                Coordinate.of(0.0,0.0,1.0),
                                                Coordinate.of(0.5,0.0,0.0)
                                        }
                                )
                        )
                ).setSRID(srid);
        JGeometry oracleGeometry = GeometryAdapter.toOracleGeometry(citydbGeometry);
        System.out.println("==========MultiPolygon");
        System.out.println(oracleGeometry.toStringFull());
    }

    public void testCitydbToOraclePolygonWithHole() {
        Polygon citydbGeometry =
                Polygon.of(
                        LinearRing.of(
                                new Coordinate[]{
                                        Coordinate.of(0.5,0.0,0.0),
                                        Coordinate.of(0.5,1.0,0.0),
                                        Coordinate.of(0.0,1.0,1.0),
                                        Coordinate.of(0.0,0.0,1.0),
                                        Coordinate.of(0.5,0.0,0.0)
                                }
                        ),
                        new LinearRing[]{
                                LinearRing.of(
                                        new Coordinate[]{
                                                Coordinate.of(0.25,0.5,0.5),
                                                Coordinate.of(0.15,0.5,0.7),
                                                Coordinate.of(0.15,0.6,0.7),
                                                Coordinate.of(0.25,0.6,0.5),
                                                Coordinate.of(0.25,0.5,0.5)
                                        }
                                )
                        }
                ).setSRID(srid);
        JGeometry oracleGeometry = GeometryAdapter.toOracleGeometry(citydbGeometry);
        System.out.println("==========PolygonWithHole");
        System.out.println(oracleGeometry.toStringFull());
    }

    public void testCitydbToOracleCompositeSurface() {
        CompositeSurface citydbGeometry =
                CompositeSurface.of(
                        Polygon.of(
                                LinearRing.of(
                                        new Coordinate[]{
                                                Coordinate.of(0.5,0.0,0.0),
                                                Coordinate.of(0.5,1.0,0.0),
                                                Coordinate.of(0.0,1.0,0.0),
                                                Coordinate.of(0.0,0.0,0.0),
                                                Coordinate.of(0.5,0.0,0.0)
                                        }
                                ),
                                new LinearRing[]{
                                        LinearRing.of(
                                                new Coordinate[]{
                                                        Coordinate.of(1.5,0.0,0.0),
                                                        Coordinate.of(2.5,1.0,0.0),
                                                        Coordinate.of(1.5,2.0,0.0),
                                                        Coordinate.of(0.5,2.0,0.0),
                                                        Coordinate.of(0.5,0.0,0.0),
                                                        Coordinate.of(1.5,0.0,0.0)
                                                }
                                        )
                                }
                        )
                ).setSRID(srid);
        JGeometry oracleGeometry = GeometryAdapter.toOracleGeometry(citydbGeometry);
        System.out.println("==========CompositeSurface");
        System.out.println(oracleGeometry.toStringFull());
    }

    public void testCitydbToOracleSurfaceWithHole() {
        CompositeSurface citydbGeometry =
                CompositeSurface.of(
                        Polygon.of(
                                LinearRing.of(
                                        new Coordinate[]{
                                                Coordinate.of(0.5, 0.0, 0.0),
                                                Coordinate.of(0.5, 1.0, 0.0),
                                                Coordinate.of(0.0, 1.0, 1.0),
                                                Coordinate.of(0.0, 0.0, 1.0),
                                                Coordinate.of(0.5, 0.0, 0.0)
                                        }
                                ),
                                new LinearRing[]{
                                        LinearRing.of(
                                                new Coordinate[]{
                                                        Coordinate.of(0.25, 0.5, 0.5),
                                                        Coordinate.of(0.15, 0.5, 0.7),
                                                        Coordinate.of(0.15, 0.6, 0.7),
                                                        Coordinate.of(0.25, 0.6, 0.5),
                                                        Coordinate.of(0.25, 0.5, 0.5)
                                                }
                                        )
                                }
                        ),
                        Polygon.of(
                                LinearRing.of(
                                        new Coordinate[]{
                                                Coordinate.of(1.5,0.0,0.0),
                                                Coordinate.of(2.5,1.0,0.0),
                                                Coordinate.of(1.5,2.0,0.0),
                                                Coordinate.of(0.5,2.0,0.0),
                                                Coordinate.of(0.5,0.0,0.0),
                                                Coordinate.of(1.5,0.0,0.0)
                                        }
                                )
                        )
                ).setSRID(srid);
        JGeometry oracleGeometry = GeometryAdapter.toOracleGeometry(citydbGeometry);
        System.out.println("==========SurfaceWithHoles");
        System.out.println(oracleGeometry.toStringFull());
    }

    public void testCitydbToOracleMultiSurface() {
        MultiSurface citydbGeometry =
                MultiSurface.of(
                        Polygon.of(
                                LinearRing.of(
                                        new Coordinate[]{
                                                Coordinate.of(6.0,6.0,6.0),
                                                Coordinate.of(5.0,6.0,10.0),
                                                Coordinate.of(3.0,4.0,8.0),
                                                Coordinate.of(4.0,4.0,4.0),
                                                Coordinate.of(6.0,6.0,6.0)
                                        }
                                )
                        ),
                        Polygon.of(
                                LinearRing.of(
                                        new Coordinate[]{
                                                Coordinate.of(0.5,0.0,0.0),
                                                Coordinate.of(0.5,1.0,0.0),
                                                Coordinate.of(0.0,1.0,1.0),
                                                Coordinate.of(0.0,0.0,1.0),
                                                Coordinate.of(0.5,0.0,0.0)
                                        }
                                )
                        ),
                        Polygon.of(
                                LinearRing.of(
                                        new Coordinate[]{
                                                Coordinate.of(0.5,0.0,0.0),
                                                Coordinate.of(0.5,1.0,0.0),
                                                Coordinate.of(0.0,1.0,1.0),
                                                Coordinate.of(0.0,0.0,1.0),
                                                Coordinate.of(0.5,0.0,0.0)
                                        }
                                ),
                                new LinearRing[]{
                                        LinearRing.of(
                                                new Coordinate[]{
                                                        Coordinate.of(0.25,0.5,0.5),
                                                        Coordinate.of(0.15,0.5,0.7),
                                                        Coordinate.of(0.15,0.6,0.7),
                                                        Coordinate.of(0.25,0.6,0.5),
                                                        Coordinate.of(0.25,0.5,0.5)
                                                }
                                        )
                                }
                        )
                ).setSRID(srid);
        JGeometry oracleGeometry = GeometryAdapter.toOracleGeometry(citydbGeometry);
        System.out.println("==========MultiSurface");
        System.out.println(oracleGeometry.toStringFull());
    }

    public void testCitydbToOracleSolid() {
        Solid citydbGeometry =
                Solid.of(
                        CompositeSurface.of(
                                Polygon.of(
                                        LinearRing.of(
                                                new Coordinate[]{
                                                        Coordinate.of(1.0,0.0,-1.0),
                                                        Coordinate.of(1.0,1.0,-1.0),
                                                        Coordinate.of(1.0,1.0,1.0),
                                                        Coordinate.of(1.0,0.0,1.0),
                                                        Coordinate.of(1.0,0.0,-1.0),
                                                }
                                        )
                                ),
                                Polygon.of(
                                        LinearRing.of(
                                                new Coordinate[]{
                                                        Coordinate.of(1.0,0.0,1.0),
                                                        Coordinate.of(0.0,0.0,1.0),
                                                        Coordinate.of(0.0,0.0,-1.0),
                                                        Coordinate.of(1.0,0.0,-1.0),
                                                        Coordinate.of(1.0,0.0,1.0)
                                                }
                                        )
                                ),
                                Polygon.of(
                                        LinearRing.of(
                                                new Coordinate[]{
                                                        Coordinate.of(0.0,1.0,1.0),
                                                        Coordinate.of(0.0,1.0,-1.0),
                                                        Coordinate.of(0.0,0.0,-1.0),
                                                        Coordinate.of(0.0,0.0,1.0),
                                                        Coordinate.of(0.0,1.0,1.0)
                                                }
                                        )
                                ),
                                Polygon.of(
                                        LinearRing.of(
                                                new Coordinate[]{
                                                        Coordinate.of(1.0,1.0,-1.0),
                                                        Coordinate.of(0.0,1.0,-1.0),
                                                        Coordinate.of(0.0,1.0,1.0),
                                                        Coordinate.of(1.0,1.0,1.0),
                                                        Coordinate.of(1.0,1.0,-1.0)
                                                }
                                        )
                                ),
                                Polygon.of(
                                        LinearRing.of(
                                                new Coordinate[]{
                                                        Coordinate.of(1.0,1.0,1.0),
                                                        Coordinate.of(0.0,1.0,1.0),
                                                        Coordinate.of(0.0,0.0,1.0),
                                                        Coordinate.of(1.0,0.0,1.0),
                                                        Coordinate.of(1.0,1.0,1.0)
                                                }
                                        )
                                ),
                                Polygon.of(
                                        LinearRing.of(
                                                new Coordinate[]{
                                                        Coordinate.of(1.0,1.0,-1.0),
                                                        Coordinate.of(1.0,0.0,-1.0),
                                                        Coordinate.of(0.0,0.0,-1.0),
                                                        Coordinate.of(0.0,1.0,-1.0),
                                                        Coordinate.of(1.0,1.0,-1.0)
                                                }
                                        )
                                )
                        )
                ).setSRID(srid);
        JGeometry oracleGeometry = GeometryAdapter.toOracleGeometry(citydbGeometry);
        System.out.println("==========Solid");
        System.out.println(oracleGeometry.toStringFull());
    }

    public void testCitydbToOracleCompositeSolid() {
        CompositeSolid citydbGeometry =
                CompositeSolid.of(
                        Solid.of(
                                CompositeSurface.of(
                                        Polygon.of(
                                                LinearRing.of(
                                                        new Coordinate[]{
                                                                Coordinate.of(-2.0,1.0,3.0),
                                                                Coordinate.of(-2.0,1.0,0.0),
                                                                Coordinate.of(-3.0,1.0,0.0),
                                                                Coordinate.of(-3.0,1.0,3.0),
                                                                Coordinate.of(-2.0,1.0,3.0)
                                                        }
                                                )
                                        ),
                                        Polygon.of(
                                                LinearRing.of(
                                                        new Coordinate[]{
                                                                Coordinate.of(-3.0,1.0,3.0),
                                                                Coordinate.of(-3.0,1.0,0.0),
                                                                Coordinate.of(-3.0,-1.0,0.0),
                                                                Coordinate.of(-3.0,-1.0,3.0),
                                                                Coordinate.of(-3.0,1.0,3.0)
                                                        }
                                                )
                                        ),
                                        Polygon.of(
                                                LinearRing.of(
                                                        new Coordinate[]{
                                                                Coordinate.of(-3.0,-1.0,3.0),
                                                                Coordinate.of(-3.0,-1.0,0.0),
                                                                Coordinate.of(-2.0,-1.0,0.0),
                                                                Coordinate.of(-2.0,-1.0,3.0),
                                                                Coordinate.of(-3.0,-1.0,3.0)
                                                        }
                                                )
                                        ),
                                        Polygon.of(
                                                LinearRing.of(
                                                        new Coordinate[]{
                                                                Coordinate.of(-2.0,-1.0,3.0),
                                                                Coordinate.of(-2.0,-1.0,0.0),
                                                                Coordinate.of(-2.0,1.0,0.0),
                                                                Coordinate.of(-2.0,1.0,3.0),
                                                                Coordinate.of(-2.0,-1.0,3.0)
                                                        }
                                                )
                                        ),
                                        Polygon.of(
                                                LinearRing.of(
                                                        new Coordinate[]{
                                                                Coordinate.of(-2.0,-1.0,3.0),
                                                                Coordinate.of(-2.0,1.0,3.0),
                                                                Coordinate.of(-3.0,1.0,3.0),
                                                                Coordinate.of(-3.0,-1.0,3.0),
                                                                Coordinate.of(-2.0,-1.0,3.0)
                                                        }
                                                )
                                        ),
                                        Polygon.of(
                                                LinearRing.of(
                                                        new Coordinate[]{
                                                                Coordinate.of(-2.0,1.0,0.0),
                                                                Coordinate.of(-2.0,-1.0,0.0),
                                                                Coordinate.of(-3.0,-1.0,0.0),
                                                                Coordinate.of(-3.0,1.0,0.0),
                                                                Coordinate.of(-2.0,1.0,0.0)
                                                        }
                                                )
                                        )
                                )
                        ),
                        Solid.of(
                                CompositeSurface.of(
                                        Polygon.of(
                                                LinearRing.of(
                                                        new Coordinate[]{
                                                                Coordinate.of(2.0,0.0,3.0),
                                                                Coordinate.of(2.0,0.0,0.0),
                                                                Coordinate.of(4.0,2.0,0.0),
                                                                Coordinate.of(4.0,2.0,3.0),
                                                                Coordinate.of(2.0,0.0,3.0)
                                                        }
                                                )
                                        ),
                                        Polygon.of(
                                                LinearRing.of(
                                                        new Coordinate[]{
                                                                Coordinate.of(4.5,-2.0,3.0),
                                                                Coordinate.of(4.5,-2.0,0.0),
                                                                Coordinate.of(2.0,0.0,0.0),
                                                                Coordinate.of(2.0,0.0,3.0),
                                                                Coordinate.of(4.5,-2.0,3.0)
                                                        }
                                                )
                                        ),
                                        Polygon.of(
                                                LinearRing.of(
                                                        new Coordinate[]{
                                                                Coordinate.of(4.5,-2.0,3.0),
                                                                Coordinate.of(-2.0,-2.0,3.0),
                                                                Coordinate.of(-2.0,-2.0,0.0),
                                                                Coordinate.of(4.5,-2.0,0.0),
                                                                Coordinate.of(4.5,-2.0,3.0)
                                                        }
                                                )
                                        ),
                                        Polygon.of(
                                                LinearRing.of(
                                                        new Coordinate[]{
                                                                Coordinate.of(-2.0,-2.0,3.0),
                                                                Coordinate.of(-2.0,2.0,3.0),
                                                                Coordinate.of(-2.0,2.0,0.0),
                                                                Coordinate.of(-2.0,-2.0,0.0),
                                                                Coordinate.of(-2.0,-2.0,3.0)
                                                        }
                                                )
                                        ),
                                        Polygon.of(
                                                LinearRing.of(
                                                        new Coordinate[]{
                                                                Coordinate.of(4.0,2.0,3.0),
                                                                Coordinate.of(4.0,2.0,0.0),
                                                                Coordinate.of(-2.0,2.0,0.0),
                                                                Coordinate.of(-2.0,2.0,3.0),
                                                                Coordinate.of(4.0,2.0,3.0)
                                                        }
                                                )
                                        ),
                                        Polygon.of(
                                                LinearRing.of(
                                                        new Coordinate[]{
                                                                Coordinate.of(2.0,0.0,3.0),
                                                                Coordinate.of(4.0,2.0,3.0),
                                                                Coordinate.of(-2.0,2.0,3.0),
                                                                Coordinate.of(-2.0,-2.0,3.0),
                                                                Coordinate.of(4.5,-2.0,3.0),
                                                                Coordinate.of(2.0,0.0,3.0)
                                                        }
                                                )
                                        ),
                                        Polygon.of(
                                                LinearRing.of(
                                                        new Coordinate[]{
                                                                Coordinate.of(2.0,0.0,0.0),
                                                                Coordinate.of(4.5,-2.0,0.0),
                                                                Coordinate.of(-2.0,-2.0,0.0),
                                                                Coordinate.of(-2.0,2.0,0.0),
                                                                Coordinate.of(4.0,2.0,0.0),
                                                                Coordinate.of(2.0,0.0,0.0)
                                                        }
                                                )
                                        )
                                )
                        )
                ).setSRID(srid);
        JGeometry oracleGeometry = GeometryAdapter.toOracleGeometry(citydbGeometry);
        System.out.println("==========CompositeSolid");
        System.out.println(oracleGeometry.toStringFull());
    }

    public void testCitydbToOracleMultiSolid() {
        MultiSolid citydbGeometry =
                MultiSolid.of(
                        Solid.of(
                                CompositeSurface.of(
                                        Polygon.of(
                                                LinearRing.of(
                                                        new Coordinate[]{
                                                                Coordinate.of(1.0, 0.0, 4.0),
                                                                Coordinate.of(1.0, 1.0, 4.0),
                                                                Coordinate.of(1.0, 1.0, 6.0),
                                                                Coordinate.of(1.0, 0.0, 6.0),
                                                                Coordinate.of(1.0, 0.0, 4.0)
                                                        }
                                                )
                                        ),
                                        Polygon.of(
                                                LinearRing.of(
                                                        new Coordinate[]{
                                                                Coordinate.of(1.0, 0.0, 6.0),
                                                                Coordinate.of(0.0, 0.0, 6.0),
                                                                Coordinate.of(0.0, 0.0, 4.0),
                                                                Coordinate.of(1.0, 0.0, 4.0),
                                                                Coordinate.of(1.0, 0.0, 6.0)
                                                        }
                                                )
                                        ),
                                        Polygon.of(
                                                LinearRing.of(
                                                        new Coordinate[]{
                                                                Coordinate.of(0.0, 1.0, 6.0),
                                                                Coordinate.of(0.0, 1.0, 4.0),
                                                                Coordinate.of(0.0, 0.0, 4.0),
                                                                Coordinate.of(0.0, 0.0, 6.0),
                                                                Coordinate.of(0.0, 1.0, 6.0)
                                                        }
                                                )
                                        ),
                                        Polygon.of(
                                                LinearRing.of(
                                                        new Coordinate[]{
                                                                Coordinate.of(1.0, 1.0, 4.0),
                                                                Coordinate.of(0.0, 1.0, 4.0),
                                                                Coordinate.of(0.0, 1.0, 6.0),
                                                                Coordinate.of(1.0, 1.0, 6.0),
                                                                Coordinate.of(1.0, 1.0, 4.0)
                                                        }
                                                )
                                        ),
                                        Polygon.of(
                                                LinearRing.of(
                                                        new Coordinate[]{
                                                                Coordinate.of(1.0, 1.0, 6.0),
                                                                Coordinate.of(0.0, 1.0, 6.0),
                                                                Coordinate.of(0.0, 0.0, 6.0),
                                                                Coordinate.of(1.0, 0.0, 6.0),
                                                                Coordinate.of(1.0, 1.0, 6.0)
                                                        }
                                                )
                                        ),
                                        Polygon.of(
                                                LinearRing.of(
                                                        new Coordinate[]{
                                                                Coordinate.of(1.0, 1.0, 4.0),
                                                                Coordinate.of(1.0, 0.0, 4.0),
                                                                Coordinate.of(0.0, 0.0, 4.0),
                                                                Coordinate.of(0.0, 1.0, 4.0),
                                                                Coordinate.of(1.0, 1.0, 4.0)
                                                        }
                                                )
                                        )
                                )
                        ),
                        Solid.of(
                                CompositeSurface.of(
                                        Polygon.of(
                                                LinearRing.of(
                                                        new Coordinate[]{
                                                                Coordinate.of(2.0, 0.0, 3.0),
                                                                Coordinate.of(2.0, 0.0, 0.0),
                                                                Coordinate.of(4.0, 2.0, 0.0),
                                                                Coordinate.of(4.0, 2.0, 3.0),
                                                                Coordinate.of(2.0, 0.0, 3.0)
                                                        }
                                                )
                                        ),
                                        Polygon.of(
                                                LinearRing.of(
                                                        new Coordinate[]{
                                                                Coordinate.of(4.5, -2.0, 3.0),
                                                                Coordinate.of(4.5, -2.0, 0.0),
                                                                Coordinate.of(2.0, 0.0, 0.0),
                                                                Coordinate.of(2.0, 0.0, 3.0),
                                                                Coordinate.of(4.5, -2.0, 3.0)
                                                        }
                                                )
                                        ),
                                        Polygon.of(
                                                LinearRing.of(
                                                        new Coordinate[]{
                                                                Coordinate.of(4.5, -2.0, 3.0),
                                                                Coordinate.of(-2.0, -2.0, 3.0),
                                                                Coordinate.of(-2.0, -2.0, 0.0),
                                                                Coordinate.of(4.5, -2.0, 0.0),
                                                                Coordinate.of(4.5, -2.0, 3.0)
                                                        }
                                                )
                                        ),
                                        Polygon.of(
                                                LinearRing.of(
                                                        new Coordinate[]{
                                                                Coordinate.of(-2.0, -2.0, 3.0),
                                                                Coordinate.of(-2.0, 2.0, 3.0),
                                                                Coordinate.of(-2.0, 2.0, 0.0),
                                                                Coordinate.of(-2.0, -2.0, 0.0),
                                                                Coordinate.of(-2.0, -2.0, 3.0)
                                                        }
                                                )
                                        ),
                                        Polygon.of(
                                                LinearRing.of(
                                                        new Coordinate[]{
                                                                Coordinate.of(4.0, 2.0, 3.0),
                                                                Coordinate.of(4.0, 2.0, 0.0),
                                                                Coordinate.of(-2.0, 2.0, 0.0),
                                                                Coordinate.of(-2.0, 2.0, 3.0),
                                                                Coordinate.of(4.0, 2.0, 3.0)
                                                        }
                                                )
                                        ),
                                        Polygon.of(
                                                LinearRing.of(
                                                        new Coordinate[]{
                                                                Coordinate.of(2.0, 0.0, 3.0),
                                                                Coordinate.of(4.0, 2.0, 3.0),
                                                                Coordinate.of(-2.0, 2.0, 3.0),
                                                                Coordinate.of(-2.0, -2.0, 3.0),
                                                                Coordinate.of(4.5, -2.0, 3.0),
                                                                Coordinate.of(2.0, 0.0, 3.0)

                                                        }
                                                )
                                        ),
                                        Polygon.of(
                                                LinearRing.of(
                                                        new Coordinate[]{
                                                                Coordinate.of(2.0, 0.0, 0.0),
                                                                Coordinate.of(4.5, -2.0, 0.0),
                                                                Coordinate.of(-2.0, -2.0, 0.0),
                                                                Coordinate.of(-2.0, 2.0, 0.0),
                                                                Coordinate.of(4.0, 2.0, 0.0),
                                                                Coordinate.of(2.0, 0.0, 0.0)
                                                        }
                                                )
                                        )

                                )
                        )
                ).setSRID(srid);
        JGeometry oracleGeometry = GeometryAdapter.toOracleGeometry(citydbGeometry);
        System.out.println("==========MultiSolid");
        System.out.println(oracleGeometry.toStringFull());
    }


    public static void main(String[] args){
        GeometryAdapterTest test = new GeometryAdapterTest();
        //test oracle to citydb geometry conversion
        test.testOracleToCitydbPoint();
        test.testOracleToCitydbMultiPoint();
        test.testOracleToCitydbLinearString();
        test.testOracleToCitydbMultiLinearString();
        test.testOracleToCitydbPolygon();
        test.testOracleToCitydbPolygonWithHole();
        test.testOracleToCitydbMultiPolygon();
        test.testOracleToCitydbCompositeSurface();
        test.testOracleToCitydbSurfaceWithHole();
        test.testOracleToCitydbMultiSurface();
        test.testOracleToCitydbSolid();
        test.testOracleToCitydbCompositeSolid();
        test.testOracleToCitydbMultiSolid();

        //test citydb to oracle geometry conversion
        test.testCitydbToOraclePoint();
        test.testCitydbToOracleMultiPoint();
        test.testCitydbToOracleLinearString();
        test.testCitydbToOracleMultiLinearString();
        test.testCitydbToOraclePolygon();
        test.testCitydbToOraclePolygonWithHole();
        test.testCitydbToOracleMultiPolygon();
        test.testCitydbToOracleCompositeSurface();
        test.testCitydbToOracleSurfaceWithHole();
        test.testCitydbToOracleMultiSurface();
        test.testCitydbToOracleSolid();
        test.testCitydbToOracleCompositeSolid();
        test.testCitydbToOracleMultiSolid();
    }
}
