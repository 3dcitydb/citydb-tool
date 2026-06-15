/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.geometry;

import org.citydb.model.common.Name;
import org.citydb.model.geometry.Coordinate;
import org.citydb.model.geometry.LinearRing;
import org.citydb.model.geometry.Polygon;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression tests for ear-clipping triangulation, pinning the behaviour the
 * duplicated containment check in {@code earClip} guards. The assertions are
 * deliberately geometric rather than structural: a faithful triangulation
 * exactly tiles the polygon, so the summed triangle area must equal the
 * polygon area (a hole left filled, or overlapping ears produced by a broken
 * containment test, would change that sum), and no triangle may strictly
 * contain another vertex.
 *
 * <p>Coordinates are given as lon/lat degrees with height 0; the triangulator
 * scales to metres internally but emits the original degree coordinates, so
 * areas below are computed in degree space.
 */
class PolygonTriangulatorTest {

    private static final Name WALL = Name.of("WallSurface");
    private static final double EPS = 1e-6;

    /** Build a closed {@link LinearRing} from (x,y) pairs; caller passes the
     * closing point explicitly. */
    private static LinearRing ring(double[]... xy) {
        List<Coordinate> pts = new ArrayList<>(xy.length);
        for (double[] p : xy) {
            pts.add(Coordinate.of(p[0], p[1], 0.0));
        }
        return LinearRing.of(pts);
    }

    private static TriangleMesh triangulate(Polygon polygon) {
        // No appearance data: all three lanes null (untextured, uncolored).
        return new PolygonTriangulator()
                .triangulate(polygon, 1L, WALL, new RingAttributes(null, null, null));
    }

    /** 2D area of a mesh triangle in the XY (degree) plane. */
    private static double triangleArea(TriangleMesh mesh, int[] tri) {
        List<double[]> p = mesh.getPositions();
        double[] a = p.get(tri[0]), b = p.get(tri[1]), c = p.get(tri[2]);
        return 0.5 * Math.abs((b[0] - a[0]) * (c[1] - a[1]) - (c[0] - a[0]) * (b[1] - a[1]));
    }

    private static double totalArea(TriangleMesh mesh) {
        double sum = 0;
        for (int[] tri : mesh.getTriangles()) {
            sum += triangleArea(mesh, tri);
        }
        return sum;
    }

    /** Whether point q lies strictly inside triangle (a,b,c) in the XY plane. */
    private static boolean strictlyInside(double[] q, double[] a, double[] b, double[] c) {
        double d1 = cross(a, b, q);
        double d2 = cross(b, c, q);
        double d3 = cross(c, a, q);
        boolean hasNeg = d1 < -EPS || d2 < -EPS || d3 < -EPS;
        boolean hasPos = d1 > EPS || d2 > EPS || d3 > EPS;
        return !(hasNeg && hasPos)
                && Math.abs(d1) > EPS && Math.abs(d2) > EPS && Math.abs(d3) > EPS;
    }

    private static double cross(double[] o, double[] a, double[] b) {
        return (a[0] - o[0]) * (b[1] - o[1]) - (a[1] - o[1]) * (b[0] - o[0]);
    }

    private static void assertNoTriangleContainsAnotherVertex(TriangleMesh mesh) {
        List<double[]> pos = mesh.getPositions();
        for (int[] tri : mesh.getTriangles()) {
            double[] a = pos.get(tri[0]), b = pos.get(tri[1]), c = pos.get(tri[2]);
            for (int v = 0; v < pos.size(); v++) {
                if (v == tri[0] || v == tri[1] || v == tri[2]) continue;
                assertFalse(strictlyInside(pos.get(v), a, b, c),
                        "vertex " + v + " lies inside a triangle — triangulation overlaps");
            }
        }
    }

    @Test
    void convexQuadTilesIntoTwoTriangles() {
        Polygon poly = Polygon.of(ring(
                new double[]{0, 0}, new double[]{1, 0},
                new double[]{1, 1}, new double[]{0, 1}, new double[]{0, 0}));

        TriangleMesh mesh = triangulate(poly);

        assertEquals(2, mesh.getTriangleCount());
        assertEquals(4, mesh.getVertexCount());
        assertEquals(1.0, totalArea(mesh), EPS);
        assertNoTriangleContainsAnotherVertex(mesh);
    }

    @Test
    void concaveLShapeTriangulatesWithoutOverlap() {
        // L-shape, area = 3 (a 2x2 square minus its top-right 1x1 quadrant).
        Polygon poly = Polygon.of(ring(
                new double[]{0, 0}, new double[]{2, 0}, new double[]{2, 1},
                new double[]{1, 1}, new double[]{1, 2}, new double[]{0, 2},
                new double[]{0, 0}));

        TriangleMesh mesh = triangulate(poly);

        // 6 distinct vertices -> n-2 = 4 triangles for a simple polygon.
        assertEquals(6, mesh.getVertexCount());
        assertEquals(4, mesh.getTriangleCount());
        // The reflex vertex must NOT be swallowed: area stays 3, not 4.
        assertEquals(3.0, totalArea(mesh), EPS);
        assertNoTriangleContainsAnotherVertex(mesh);
    }

    /**
     * Characterization test for the keyhole-bridging + forced-triple fallback
     * path, which is the SECOND site of the containment check being
     * deduplicated. A 4x4 outer square with a centred 1x1 hole drives the
     * bridge into the fallback branch. The values below pin the triangulator's
     * <em>current</em> deterministic output (the bridged ring is not perfectly
     * tiled — net area 17 rather than the geometric ideal 15), so the
     * containment-check extraction can be proven to leave the fallback path
     * byte-for-byte unchanged. If the bridging/fallback is later improved,
     * update these expected values deliberately.
     */
    @Test
    void bridgedHoleFallbackOutputIsStable() {
        LinearRing outer = ring(
                new double[]{0, 0}, new double[]{4, 0},
                new double[]{4, 4}, new double[]{0, 4}, new double[]{0, 0});
        LinearRing hole = ring(
                new double[]{1, 1}, new double[]{2, 1},
                new double[]{2, 2}, new double[]{1, 2}, new double[]{1, 1});

        TriangleMesh mesh = triangulate(Polygon.of(outer, List.of(hole)));

        // 4 outer + 4 hole + 2 bridge-duplicate vertices = 10 -> 8 triangles.
        assertEquals(10, mesh.getVertexCount());
        assertEquals(8, mesh.getTriangleCount());
        assertEquals(17.0, totalArea(mesh), 1e-4);
    }

    @Test
    void degeneratePolygonProducesNoTriangles() {
        // All points collinear -> zero-area, no valid normal, dropped.
        Polygon poly = Polygon.of(ring(
                new double[]{0, 0}, new double[]{1, 0},
                new double[]{2, 0}, new double[]{0, 0}));

        TriangleMesh mesh = triangulate(poly);

        assertTrue(mesh.isEmpty());
    }
}
