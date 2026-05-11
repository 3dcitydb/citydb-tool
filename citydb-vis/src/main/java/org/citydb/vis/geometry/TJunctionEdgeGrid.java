/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.geometry;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Uniform 3D hash grid over triangle edges, used by
 * {@link TriangleMesh#resolveTJunctions} to look up candidate edges near a
 * vertex without scanning the whole triangle list. Package-private and tied
 * to that algorithm's coordinate space (pre-scaled metres) and tolerance
 * regime (centimetres); not a general-purpose spatial index.
 */
final class TJunctionEdgeGrid {
    // Cell size (m). Tolerance is ~2 cm; 0.20 m gives ~10× headroom so a
    // vertex query expands into at most 1–8 cells. Long edges (BIM walls,
    // floor slabs) are inserted into every cell their AABB touches.
    private static final double CELL_SIZE_M = 0.20;

    private final Map<Long, IntList> grid;

    private TJunctionEdgeGrid(Map<Long, IntList> grid) {
        this.grid = grid;
    }

    static int cellOf(double coordMeters) {
        return (int) Math.floor(coordMeters / CELL_SIZE_M);
    }

    // Pack three signed cell coordinates into a single long, 21 bits per
    // axis. Absolute cell indices can be very large because mPos lives in
    // (lon × m/deg, lat × m/deg, z) space — at lon 13° that's almost
    // 1 000 km from the prime meridian and indices reach ~5 million. The
    // pack is collision-free as long as the *intra-feature delta* between
    // any two cells stays below 2^21 cells (≈ 419 km along the 0.20 m
    // grid), which holds trivially for a single building or BIM feature.
    private static long packCell(int cx, int cy, int cz) {
        return ((long) (cx & 0x1FFFFF))
                | (((long) (cy & 0x1FFFFF)) << 21)
                | (((long) (cz & 0x1FFFFF)) << 42);
    }

    static TJunctionEdgeGrid build(double[][] mPos, List<int[]> tris, int triCount) {
        Map<Long, IntList> grid = new HashMap<>();
        for (int ti = 0; ti < triCount; ti++) {
            int[] tri = tris.get(ti);
            for (int e = 0; e < 3; e++) {
                int ia = tri[e];
                int ib = tri[(e + 1) % 3];
                double ax = mPos[ia][0], ay = mPos[ia][1], az = mPos[ia][2];
                double bx = mPos[ib][0], by = mPos[ib][1], bz = mPos[ib][2];
                double dx = bx - ax, dy = by - ay, dz = bz - az;
                if (dx * dx + dy * dy + dz * dz < 1e-10) continue;
                insertEdge(grid, ti * 3 + e, ax, ay, az, bx, by, bz, dx, dy, dz);
            }
        }
        return new TJunctionEdgeGrid(grid);
    }

    // 3D DDA voxel traversal (Amanatides–Woo): insert edgeId only into the
    // cells the segment actually crosses, not the full AABB. For axis-aligned
    // BIM edges this is equivalent to the AABB walk; for diagonal LoD2 roof
    // edges (5 m × 5 m × 2.5 m) it cuts cell inserts from ~7 500 to ~60.
    private static void insertEdge(Map<Long, IntList> grid, int edgeId,
                                   double ax, double ay, double az,
                                   double bx, double by, double bz,
                                   double dx, double dy, double dz) {
        int cx = cellOf(ax), cy = cellOf(ay), cz = cellOf(az);
        int cxEnd = cellOf(bx), cyEnd = cellOf(by), czEnd = cellOf(bz);

        int stepX = Double.compare(dx, 0);   // -1, 0, +1
        int stepY = Double.compare(dy, 0);
        int stepZ = Double.compare(dz, 0);

        // tDelta = parametric distance along the segment to cross one cell
        // along each axis; tMax = parametric t to reach the next cell
        // boundary from the start point.
        double tDeltaX = (stepX != 0) ? Math.abs(CELL_SIZE_M / dx) : Double.POSITIVE_INFINITY;
        double tDeltaY = (stepY != 0) ? Math.abs(CELL_SIZE_M / dy) : Double.POSITIVE_INFINITY;
        double tDeltaZ = (stepZ != 0) ? Math.abs(CELL_SIZE_M / dz) : Double.POSITIVE_INFINITY;

        double tMaxX = (stepX > 0) ? ((cx + 1) * CELL_SIZE_M - ax) / dx
                : (stepX < 0) ? (cx * CELL_SIZE_M - ax) / dx
                : Double.POSITIVE_INFINITY;
        double tMaxY = (stepY > 0) ? ((cy + 1) * CELL_SIZE_M - ay) / dy
                : (stepY < 0) ? (cy * CELL_SIZE_M - ay) / dy
                : Double.POSITIVE_INFINITY;
        double tMaxZ = (stepZ > 0) ? ((cz + 1) * CELL_SIZE_M - az) / dz
                : (stepZ < 0) ? (cz * CELL_SIZE_M - az) / dz
                : Double.POSITIVE_INFINITY;

        // Iteration cap as a numerical-safety net — the Manhattan distance
        // between the start and end cells is the theoretical maximum cells
        // a line can cross plus 1 per starting cell.
        int maxSteps = Math.abs(cxEnd - cx) + Math.abs(cyEnd - cy) + Math.abs(czEnd - cz) + 1;
        for (int step = 0; step <= maxSteps; step++) {
            grid.computeIfAbsent(packCell(cx, cy, cz), k -> new IntList()).add(edgeId);
            if (cx == cxEnd && cy == cyEnd && cz == czEnd) return;
            if (tMaxX < tMaxY) {
                if (tMaxX < tMaxZ) {
                    cx += stepX;
                    tMaxX += tDeltaX;
                } else {
                    cz += stepZ;
                    tMaxZ += tDeltaZ;
                }
            } else if (tMaxY < tMaxZ) {
                cy += stepY;
                tMaxY += tDeltaY;
            } else {
                cz += stepZ;
                tMaxZ += tDeltaZ;
            }
        }
    }

    IntList bucket(int cx, int cy, int cz) {
        return grid.get(packCell(cx, cy, cz));
    }

    // Primitive-backed int list — the T-junction grid buckets must avoid
    // Integer boxing, since a dense mesh near the size ceiling produces
    // tens of millions of bucket inserts and reads per resolveTJunctions
    // iteration. Fields are package-private so the hot loop in
    // TriangleMesh.resolveTJunctions can read them without method calls.
    static final class IntList {
        int[] data = new int[4];
        int size = 0;

        void add(int v) {
            if (size == data.length) {
                int[] grown = new int[data.length << 1];
                System.arraycopy(data, 0, grown, 0, size);
                data = grown;
            }
            data[size++] = v;
        }
    }
}
