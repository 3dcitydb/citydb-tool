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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
        return new PolygonTriangulator(false)
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

    // ---- outline-edge mask (CESIUM_primitive_outline provenance) ----

    /**
     * Collect all outlined triangle edges as winding-independent position-pair
     * keys, so assertions compare against ring geometry rather than vertex
     * indices (which shift with bridging duplicates and winding reversal).
     */
    private static Set<String> outlinedEdgeKeys(TriangleMesh mesh) {
        Set<String> keys = new HashSet<>();
        List<double[]> pos = mesh.getPositions();
        List<int[]> tris = mesh.getTriangles();
        for (int t = 0; t < tris.size(); t++) {
            int[] tri = tris.get(t);
            byte mask = mesh.getTriangleOutlineEdges(t);
            for (int e = 0; e < 3; e++) {
                if ((mask & (1 << e)) != 0) {
                    keys.add(edgeKey(pos.get(tri[e]), pos.get(tri[(e + 1) % 3])));
                }
            }
        }
        return keys;
    }

    private static String edgeKey(double[] p, double[] q) {
        String a = p[0] + "," + p[1];
        String b = q[0] + "," + q[1];
        return a.compareTo(b) <= 0 ? a + "|" + b : b + "|" + a;
    }

    /** Position-pair keys of a closed ring given as (x,y) corners (no closing duplicate). */
    private static Set<String> ringEdgeKeys(double[][] corners) {
        Set<String> keys = new HashSet<>();
        for (int i = 0; i < corners.length; i++) {
            double[] p = corners[i];
            double[] q = corners[(i + 1) % corners.length];
            keys.add(edgeKey(new double[]{p[0], p[1]}, new double[]{q[0], q[1]}));
        }
        return keys;
    }

    @Test
    void outlineMarksExactlyTheBoundaryEdges() {
        double[][] corners = {{0, 0}, {1, 0}, {1, 1}, {0, 1}};
        Polygon poly = Polygon.of(ring(
                corners[0], corners[1], corners[2], corners[3], corners[0]));

        TriangleMesh mesh = triangulate(poly);

        // Each quad triangle has two boundary edges plus the shared diagonal.
        for (int t = 0; t < mesh.getTriangleCount(); t++) {
            assertEquals(2, Integer.bitCount(mesh.getTriangleOutlineEdges(t) & 0x7),
                    "triangle " + t + " must outline exactly its two boundary edges");
        }
        // The outlined edges are exactly the four square sides — no diagonal.
        assertEquals(ringEdgeKeys(corners), outlinedEdgeKeys(mesh));
    }

    @Test
    void outlineSurvivesWindingReversal() {
        // Same square given clockwise: the triangulator reverses the ring
        // before ear clipping and swaps the emitted winding back. The mask is
        // computed on the final vertex order, so the outline must be identical.
        double[][] corners = {{0, 0}, {0, 1}, {1, 1}, {1, 0}};
        Polygon poly = Polygon.of(ring(
                corners[0], corners[1], corners[2], corners[3], corners[0]));

        TriangleMesh mesh = triangulate(poly);

        for (int t = 0; t < mesh.getTriangleCount(); t++) {
            assertEquals(2, Integer.bitCount(mesh.getTriangleOutlineEdges(t) & 0x7));
        }
        assertEquals(ringEdgeKeys(corners), outlinedEdgeKeys(mesh));
    }

    @Test
    void outlineSkipsKeyholeBridgesAndDiagonals() {
        // Same geometry as bridgedHoleFallbackOutputIsStable: outer square
        // with a centred hole, driving the keyhole-bridge + fallback path.
        double[][] outerCorners = {{0, 0}, {4, 0}, {4, 4}, {0, 4}};
        double[][] holeCorners = {{1, 1}, {2, 1}, {2, 2}, {1, 2}};
        LinearRing outer = ring(
                outerCorners[0], outerCorners[1], outerCorners[2], outerCorners[3],
                outerCorners[0]);
        LinearRing hole = ring(
                holeCorners[0], holeCorners[1], holeCorners[2], holeCorners[3],
                holeCorners[0]);

        TriangleMesh mesh = triangulate(Polygon.of(outer, List.of(hole)));

        Set<String> outlined = outlinedEdgeKeys(mesh);
        Set<String> outerEdges = ringEdgeKeys(outerCorners);
        Set<String> holeEdges = ringEdgeKeys(holeCorners);

        // Every outlined edge lies on an original ring — never on a bridge
        // (which connects a hole vertex to an outer vertex) or a diagonal.
        for (String key : outlined) {
            assertTrue(outerEdges.contains(key) || holeEdges.contains(key),
                    "outlined edge " + key + " is not an original ring edge");
        }
        // Both rings contribute outline edges (the hole rim is outlined too).
        assertTrue(outlined.stream().anyMatch(outerEdges::contains),
                "no outer-ring edge outlined");
        assertTrue(outlined.stream().anyMatch(holeEdges::contains),
                "no hole edge outlined");
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

    // ---- source-shape statistics (pre-triangulated data detection) ----

    @Test
    void countsTriangleSourcePolygons() {
        PolygonTriangulator triangulator = new PolygonTriangulator(false);
        RingAttributes none = new RingAttributes(null, null, null);

        // Hole-free triangle: counted as a triangle source polygon.
        triangulator.triangulate(Polygon.of(ring(
                new double[]{0, 0}, new double[]{1, 0}, new double[]{0, 1},
                new double[]{0, 0})), 1L, WALL, none);

        assertEquals(1, triangulator.getSourcePolygonCount());
        assertEquals(1, triangulator.getTriangleSourcePolygonCount());
        assertTrue(triangulator.isSourcePreTriangulated());

        // Quad: counted, but the source is no longer all-triangles.
        triangulator.triangulate(Polygon.of(ring(
                new double[]{2, 0}, new double[]{3, 0}, new double[]{3, 1},
                new double[]{2, 1}, new double[]{2, 0})), 1L, WALL, none);

        assertEquals(2, triangulator.getSourcePolygonCount());
        assertEquals(1, triangulator.getTriangleSourcePolygonCount());
        assertFalse(triangulator.isSourcePreTriangulated());
    }

    @Test
    void triangleOuterRingWithHoleIsNotATrianglePolygon() {
        PolygonTriangulator triangulator = new PolygonTriangulator(false);
        LinearRing outer = ring(
                new double[]{0, 0}, new double[]{4, 0}, new double[]{0, 4},
                new double[]{0, 0});
        LinearRing hole = ring(
                new double[]{1, 1}, new double[]{2, 1}, new double[]{1, 2},
                new double[]{1, 1});

        triangulator.triangulate(Polygon.of(outer, List.of(hole)), 1L, WALL,
                new RingAttributes(null, null, null));

        assertEquals(1, triangulator.getSourcePolygonCount());
        assertEquals(0, triangulator.getTriangleSourcePolygonCount());
        assertFalse(triangulator.isSourcePreTriangulated());
    }

    @Test
    void degenerateSourcesStayOutOfStatistics() {
        PolygonTriangulator triangulator = new PolygonTriangulator(false);
        // Nothing triangulated yet: not pre-triangulated by definition.
        assertFalse(triangulator.isSourcePreTriangulated());

        // Collinear polygon rejected by the zero-normal early-out.
        triangulator.triangulate(Polygon.of(ring(
                new double[]{0, 0}, new double[]{1, 0}, new double[]{2, 0},
                new double[]{0, 0})), 1L, WALL, new RingAttributes(null, null, null));

        assertEquals(0, triangulator.getSourcePolygonCount());
        assertFalse(triangulator.isSourcePreTriangulated());
    }

    // ---- per-feature polygon dedup (xlink shared surfaces) ----

    private static Polygon unitSquare() {
        return Polygon.of(ring(
                new double[]{0, 0}, new double[]{1, 0},
                new double[]{1, 1}, new double[]{0, 1}, new double[]{0, 0}));
    }

    @Test
    void sharedPolygonInstanceIsTriangulatedOnce() {
        PolygonTriangulator triangulator = new PolygonTriangulator(false);
        RingAttributes none = new RingAttributes(null, null, null);
        Polygon shared = unitSquare();

        TriangleMesh first = triangulator.triangulate(shared, 1L, WALL, none);
        // Same Java instance reached again (e.g. via a second geometry
        // property of the feature): dropped by the identity set even
        // without a gml:id.
        TriangleMesh second = triangulator.triangulate(shared, 1L, WALL, none);

        assertEquals(2, first.getTriangleCount());
        assertTrue(second.isEmpty(), "shared instance must not be triangulated twice");
    }

    @Test
    void xlinkCopyWithSameIdIsDroppedFirstWins() {
        PolygonTriangulator triangulator = new PolygonTriangulator(false);
        RingAttributes none = new RingAttributes(null, null, null);
        Name roof = Name.of("RoofSurface");
        Polygon original = unitSquare().setObjectId("poly-1");
        Polygon xlinkCopy = unitSquare().setObjectId("poly-1");

        TriangleMesh first = triangulator.triangulate(original, 1L, roof, none);
        TriangleMesh second = triangulator.triangulate(xlinkCopy, 1L, WALL, none);

        // First-wins: the first encounter records its surface type, the
        // distinct copy carrying the same gml:id emits nothing.
        assertEquals(2, first.getTriangleCount());
        assertEquals(roof, first.getTriangleSurfaceType(0));
        assertTrue(second.isEmpty(), "distinct instance with the same gml:id must be deduped");
    }

    @Test
    void reversedCopyWithSameIdIsKeptAsBackFace() {
        // A gml:OrientableSurface flips `reversed` on its xlink target: the
        // same gml:id with opposite orientation is a DISTINCT face (the back
        // side of a shared wall) and must survive dedup; a second reversed
        // copy then dedups within the reversed lane.
        PolygonTriangulator triangulator = new PolygonTriangulator(false);
        RingAttributes none = new RingAttributes(null, null, null);
        Polygon front = unitSquare().setObjectId("poly-1");
        Polygon back = unitSquare().setObjectId("poly-1").setReversed(true);
        Polygon backAgain = unitSquare().setObjectId("poly-1").setReversed(true);

        TriangleMesh frontMesh = triangulator.triangulate(front, 1L, WALL, none);
        TriangleMesh backMesh = triangulator.triangulate(back, 1L, WALL, none);
        TriangleMesh backDup = triangulator.triangulate(backAgain, 1L, WALL, none);

        assertEquals(2, frontMesh.getTriangleCount());
        assertEquals(2, backMesh.getTriangleCount(), "reversed copy is a distinct face");
        assertTrue(backDup.isEmpty(), "second reversed copy must be deduped");

        // The kept back face really faces the other way: the CCW square's
        // Newell normal is +Z, the reversed polygon's -Z.
        assertTrue(frontMesh.getNormals().get(0)[2] > 0);
        assertTrue(backMesh.getNormals().get(0)[2] < 0);
    }

    @Test
    void distinctPolygonsWithoutIdAreBothTriangulated() {
        // Dedup of distinct instances requires a gml:id — coincident
        // geometry without one must be kept.
        PolygonTriangulator triangulator = new PolygonTriangulator(false);
        RingAttributes none = new RingAttributes(null, null, null);

        TriangleMesh first = triangulator.triangulate(unitSquare(), 1L, WALL, none);
        TriangleMesh second = triangulator.triangulate(unitSquare(), 1L, WALL, none);

        assertEquals(2, first.getTriangleCount());
        assertEquals(2, second.getTriangleCount());
    }
}
