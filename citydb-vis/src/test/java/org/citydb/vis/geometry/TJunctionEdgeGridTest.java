/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.geometry;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Characterization tests for {@link TJunctionEdgeGrid}, the uniform hash grid
 * that feeds {@code TriangleMesh.resolveTJunctions}. Its correctness is
 * invisible at the surface (a wrong grid only shows up as cracks between
 * adjacent polygons), so these pin the behaviours the DDA insertion relies on:
 * <ul>
 *   <li>{@code cellOf} floors toward negative infinity (cells are
 *       half-open {@code [k·s, (k+1)·s)} on both sides of the origin);</li>
 *   <li>an edge is registered, under id {@code triIndex·3 + edgeIndex}, in
 *       every grid cell its segment actually crosses — and <em>only</em> those,
 *       not the full bounding box (the whole point of the Amanatides–Woo walk
 *       over a naive AABB fill);</li>
 *   <li>zero-length edges are skipped.</li>
 * </ul>
 * Coordinates are in the grid's native pre-scaled metres; the cell size is
 * 0.20 m, so {@code 0.85 m} lands in cell 4 and {@code 1.0 m} on the 5/4
 * boundary — test points avoid exact boundaries to stay robust.
 */
class TJunctionEdgeGridTest {

    /** Build a grid from a single triangle (vertices v0, v1, v2). */
    private static TJunctionEdgeGrid gridOf(double[] v0, double[] v1, double[] v2) {
        double[][] mPos = {v0, v1, v2};
        List<int[]> tris = List.of(new int[]{0, 1, 2});
        return TJunctionEdgeGrid.build(mPos, tris, tris.size());
    }

    private static boolean present(TJunctionEdgeGrid grid, int cx, int cy, int cz) {
        TJunctionEdgeGrid.IntList list = grid.bucket(cx, cy, cz);
        return list != null && list.size > 0;
    }

    private static boolean containsEdge(TJunctionEdgeGrid grid, int cx, int cy, int cz, int edgeId) {
        TJunctionEdgeGrid.IntList list = grid.bucket(cx, cy, cz);
        if (list == null) {
            return false;
        }
        for (int i = 0; i < list.size; i++) {
            if (list.data[i] == edgeId) {
                return true;
            }
        }
        return false;
    }

    @Test
    void cellOfFloorsTowardNegativeInfinity() {
        assertEquals(0, TJunctionEdgeGrid.cellOf(0.0));
        assertEquals(0, TJunctionEdgeGrid.cellOf(0.19));
        assertEquals(1, TJunctionEdgeGrid.cellOf(0.20));   // boundary belongs to the upper cell
        assertEquals(-1, TJunctionEdgeGrid.cellOf(-0.01)); // floor, not truncation
        assertEquals(-1, TJunctionEdgeGrid.cellOf(-0.20));
        assertEquals(-2, TJunctionEdgeGrid.cellOf(-0.21));
    }

    @Test
    void shortTriangleLandsAllThreeEdgesInOneCellWithCorrectIds() {
        // All three vertices inside cell (0,0,0); each edge stays within it.
        TJunctionEdgeGrid grid = gridOf(
                new double[]{0.02, 0.02, 0.02},
                new double[]{0.10, 0.02, 0.02},
                new double[]{0.02, 0.10, 0.02});

        // edgeId = triIndex*3 + edgeIndex -> 0, 1, 2 for the lone triangle.
        assertTrue(containsEdge(grid, 0, 0, 0, 0));
        assertTrue(containsEdge(grid, 0, 0, 0, 1));
        assertTrue(containsEdge(grid, 0, 0, 0, 2));
        // Nothing leaks into neighbouring cells.
        assertNull(grid.bucket(1, 0, 0));
        assertNull(grid.bucket(0, 1, 0));
        assertNull(grid.bucket(0, 0, 1));
    }

    @Test
    void axisAlignedEdgeFillsEveryCrossedCellAndNoOthers() {
        // Degenerate triangle (A, B, B): the only non-zero edges run along the
        // x-axis from A to B, isolating a single line. A in cell x=0, B in x=4.
        double[] a = {0.05, 0.05, 0.05};
        double[] b = {0.85, 0.05, 0.05};
        TJunctionEdgeGrid grid = gridOf(a, b, b);

        for (int cx = 0; cx <= 4; cx++) {
            assertTrue(present(grid, cx, 0, 0), "cell x=" + cx + " must hold the edge");
        }
        assertNull(grid.bucket(5, 0, 0), "no cell past the endpoint");
        assertNull(grid.bucket(0, 1, 0), "no off-axis cell");
    }

    @Test
    void diagonalEdgeWalksTheLineNotTheBoundingBox() {
        // Diagonal across the xy plane from cell (0,0,0) to (4,4,0). A correct
        // DDA visits the staircase along the line; an AABB fill would also
        // touch the far corners (4,0) and (0,4). Pin that those stay empty.
        double[] a = {0.05, 0.05, 0.0};
        double[] b = {0.85, 0.85, 0.0};
        TJunctionEdgeGrid grid = gridOf(a, b, b);

        // On-line cells are visited (endpoints + the x==y diagonal cells).
        assertTrue(present(grid, 0, 0, 0));
        assertTrue(present(grid, 1, 1, 0));
        assertTrue(present(grid, 2, 2, 0));
        assertTrue(present(grid, 3, 3, 0));
        assertTrue(present(grid, 4, 4, 0));
        // Far corners and cells well off the diagonal are NOT filled.
        assertFalse(present(grid, 4, 0, 0), "AABB corner must stay empty");
        assertFalse(present(grid, 0, 4, 0), "AABB corner must stay empty");
        assertNull(grid.bucket(3, 0, 0));
        assertNull(grid.bucket(0, 3, 0));
    }

    @Test
    void fullyDegenerateTriangleInsertsNothing() {
        // All three vertices coincide -> every edge is zero-length and skipped.
        double[] p = {0.05, 0.05, 0.05};
        TJunctionEdgeGrid grid = gridOf(p, p.clone(), p.clone());
        assertNull(grid.bucket(0, 0, 0));
    }
}
