/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.geometry;

import org.citydb.model.common.Name;
import org.citydb.vis.geometry.VertexWelder.WeldResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Characterization tests for {@link VertexWelder}, the geometry stage shared by
 * both writers. They pin the three load-bearing behaviours downstream encoders
 * rely on, none of which throw when wrong (they just ship corrupt geometry):
 * <ul>
 *   <li>vertices within tolerance weld to one position, but a UV difference at
 *       the same position preserves the texture seam;</li>
 *   <li>triangles that collapse to a degenerate after welding are dropped;</li>
 *   <li>surviving triangles are grouped into per-feature face ranges with the
 *       per-vertex feature index that drives the property table.</li>
 * </ul>
 * Coordinates are in degrees (the welder converts to metres via
 * {@code metersPerDegreeLon}); 0.001&deg; &asymp; 111 m is well outside the 2 cm
 * weld tolerance, so distinct corners never merge by accident.
 */
class VertexWelderTest {

    private static final Name WALL = Name.of("WallSurface");
    private static final float UP_X = 0f, UP_Y = 0f, UP_Z = 1f;

    /** Add a vertex (no UV/colour) at a lon/lat position on the ground plane. */
    private static int v(TriangleMesh mesh, double lon, double lat) {
        return mesh.addVertex(lon, lat, 0, UP_X, UP_Y, UP_Z);
    }

    @Test
    void filtersDegenerateTrianglesAndGroupsFaceRangesByFeature() {
        TriangleMesh mesh = new TriangleMesh();

        // T0, T1: feature 100 — two well-separated, non-degenerate triangles.
        mesh.addTriangle(v(mesh, 0, 0), v(mesh, 0.001, 0), v(mesh, 0, 0.001), 100L, -1, false, WALL);
        mesh.addTriangle(v(mesh, 0.002, 0), v(mesh, 0.003, 0), v(mesh, 0.002, 0.001), 100L, -1, false, WALL);

        // T2: feature 999 — two coincident corners weld together, so this
        // triangle collapses to a degenerate and must be filtered out.
        int dup0 = v(mesh, 0.004, 0);
        int dup1 = v(mesh, 0.004, 0); // identical position -> welds onto dup0
        mesh.addTriangle(dup0, dup1, v(mesh, 0.004, 0.001), 999L, -1, false, WALL);

        // T3: feature 200 — separate non-degenerate triangle.
        mesh.addTriangle(v(mesh, 0.005, 0), v(mesh, 0.006, 0), v(mesh, 0.005, 0.001), 200L, -1, false, WALL);

        WeldResult r = VertexWelder.weldAndFilter(mesh, 0, 0, 0);

        // Original triangle index 2 dropped; the rest survive in order.
        assertEquals(List.of(0, 1, 3), r.validTriIndices());
        assertEquals(9, r.vertexCount());

        // Face ranges are in compacted (post-filter) index space and split on
        // the feature-id change between the two 100s and the single 200.
        assertEquals(2, r.faceRanges().size());
        assertArrayEquals(new int[]{0, 1}, r.faceRanges().get(0));
        assertArrayEquals(new int[]{2, 2}, r.faceRanges().get(1));
        assertEquals(List.of(100L, 200L), r.rangeFeatureIds());

        // Per-vertex feature index: range 0 -> first 6 vertices, range 1 -> last 3.
        assertArrayEquals(new int[]{0, 0, 0, 0, 0, 0, 1, 1, 1}, r.computeFeatureIndices());
    }

    @Test
    void weldsCoincidentVerticesButPreservesUvSeam() {
        // Two triangles each own a corner at the exact same position. A welded
        // output vertex reuses the canonical vertex's float[] instance, so
        // reference identity tells us whether the corners merged.

        // Same UV at the shared corner -> welded (same float[] reference).
        float[][] same = weldShared(0f, 0f);
        assertSame(same[0], same[3], "matching UV at the same position must weld");

        // Different UV at the shared corner -> not welded (texture seam kept).
        float[][] seam = weldShared(0.9f, 0.9f);
        assertNotSame(seam[0], seam[3], "differing UV at the same position must NOT weld");
    }

    /**
     * Build two UV-bearing triangles whose first corner sits at the origin with
     * UV (0,0) for the first triangle and ({@code u},{@code v}) for the second,
     * then weld. Output vertex 0 is triangle 0's shared corner; output vertex 3
     * is triangle 1's shared corner.
     */
    private static float[][] weldShared(float u, float v) {
        TriangleMesh mesh = new TriangleMesh();
        int a0 = mesh.addVertex(0, 0, 0, UP_X, UP_Y, UP_Z, 0f, 0f);
        int a1 = mesh.addVertex(0.001, 0, 0, UP_X, UP_Y, UP_Z, 1f, 0f);
        int a2 = mesh.addVertex(0, 0.001, 0, UP_X, UP_Y, UP_Z, 0f, 1f);
        mesh.addTriangle(a0, a1, a2, 1L, 0, false, WALL);

        int b0 = mesh.addVertex(0, 0, 0, UP_X, UP_Y, UP_Z, u, v); // same position as a0
        int b1 = mesh.addVertex(-0.001, 0, 0, UP_X, UP_Y, UP_Z, 1f, 0f);
        int b2 = mesh.addVertex(0, -0.001, 0, UP_X, UP_Y, UP_Z, 0f, 1f);
        mesh.addTriangle(b0, b1, b2, 2L, 0, false, WALL);

        return VertexWelder.weld(mesh, 0, 0, 0);
    }
}
