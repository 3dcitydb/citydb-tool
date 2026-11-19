/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.database.geometry;

import org.citydb.model.geometry.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WKTTest {
    private static final String POINT_2D = "POINT (10.0 20.0)";
    private static final String POINT_3D = "POINT Z (10.0 20.0 30.0)";
    private static final String MULTIPOINT_3D = "MULTIPOINT Z (10.0 20.0 30.0, 11.0 21.0 31.0)";
    private static final String LINESTRING_3D = "LINESTRING Z (10.0 20.0 30.0, 11.0 21.0 31.0)";
    private static final String MULTILINESTRING_3D = "MULTILINESTRING Z ((10.0 20.0 30.0, 11.0 21.0 31.0), " +
            "(1.0 2.0 3.0, 2.0 3.0 4.0))";
    private static final String POLYGON_3D = "POLYGON Z ((10.0 20.0 30.0, 11.0 21.0 31.0, 12.0 22.0 32.0, " +
            "10.0 20.0 30.0))";
    private static final String POLYGON_3D_HOLE = "POLYGON Z ((10.0 20.0 30.0, 11.0 21.0 31.0, 12.0 22.0 32.0, " +
            "10.0 20.0 30.0), (1.0 2.0 3.0, 2.0 3.0 4.0, 3.0 4.0 5.0, 1.0 2.0 3.0))";
    private static final String MULTIPOLYGON_3D = "MULTIPOLYGON Z (((10.0 20.0 30.0, 11.0 21.0 31.0, " +
            "12.0 22.0 32.0, 10.0 20.0 30.0)), ((1.0 2.0 3.0, 2.0 3.0 4.0, 3.0 4.0 5.0, 1.0 2.0 3.0)))";
    private static final String POLYHEDRALSURFACE_3D = "POLYHEDRALSURFACE Z (((10.0 20.0 30.0, 11.0 21.0 31.0, " +
            "12.0 22.0 32.0, 10.0 20.0 30.0)), ((1.0 2.0 3.0, 2.0 3.0 4.0, 3.0 4.0 5.0, 1.0 2.0 3.0)))";
    private static final String GEOMETRYCOLLECTION_3D = "GEOMETRYCOLLECTION Z (" +
            "POLYHEDRALSURFACE Z (((1 1 1,2 2 2,3 3 3,1 1 1)),((1 1 1,2 2 2,3 3 3,1 1 1)))," +
            "POLYHEDRALSURFACE Z (((1 1 1,2 2 2,3 3 3,1 1 1)),((1 1 1,2 2 2,3 3 3,1 1 1))))";

    private final WKTParser parser = new WKTParser();
    private final WKTWriter writer = new WKTWriter();

    @Test
    void roundTripsGeometries() throws GeometryException {
        for (String wkt : List.of(POINT_2D, POINT_3D, MULTIPOINT_3D, LINESTRING_3D, MULTILINESTRING_3D,
                POLYGON_3D, POLYGON_3D_HOLE, MULTIPOLYGON_3D)) {
            assertEquals(wkt, writer.write(parser.parse(wkt)), wkt);
        }
    }

    @Test
    void roundTripsPolyhedralSurface() throws GeometryException {
        Solid solid = assertInstanceOf(Solid.class, parser.parse(POLYHEDRALSURFACE_3D));
        assertEquals(POLYHEDRALSURFACE_3D, writer.write(Solid.of(solid.getShell())));
    }

    @Test
    void parsesGeometryCollectionOfPolyhedralSurfacesAsMultiSolid() throws GeometryException {
        MultiSolid multiSolid = assertInstanceOf(MultiSolid.class, parser.parse(GEOMETRYCOLLECTION_3D));
        assertEquals(3, multiSolid.getVertexDimension());
        assertEquals(2, multiSolid.getSolids().size());
    }

    @Test
    void writesGeometryCollection() throws GeometryException {
        GeometryCollection collection = GeometryCollection.of(List.of(
                Point.of(Coordinate.of(1, 2)),
                LineString.of(List.of(Coordinate.of(0, 0), Coordinate.of(1, 1))))).setSRID(4326);

        assertEquals("SRID=4326;GEOMETRYCOLLECTION (POINT (1.0 2.0), LINESTRING (0.0 0.0, 1.0 1.0))",
                new WKTWriter().includeSRID(true).write(collection));
    }

    @Test
    void writesEmptyGeometryCollection() throws GeometryException {
        assertEquals("GEOMETRYCOLLECTION EMPTY", writer.write(GeometryCollection.of(List.of())));
    }

    @Test
    void forces3DGeometryCollection() throws GeometryException {
        GeometryCollection collection = GeometryCollection.of(List.of(Point.of(Coordinate.of(1, 2))));
        assertEquals("GEOMETRYCOLLECTION Z (POINT Z (1.0 2.0 0.0))", writer.write(collection, true));
    }

    @Test
    void writesEnvelopeAsPolygon() throws GeometryException {
        Envelope envelope = Envelope.of(Coordinate.of(0, 0, 0), Coordinate.of(1, 1, 1));
        assertEquals(writer.write(envelope.convertToPolygon()), writer.write(envelope));
    }
}
