/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences (HFT Stuttgart) <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.geometry;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Clears outline-edge bits on edges that merely subdivide a flat surface:
 * source data that models one plane as many abutting polygons (TIN walls,
 * panelized facades) marks every polygon boundary as an outline edge, which
 * renders the whole surface as a triangle wireframe. An edge loses its
 * outline when it is shared by exactly two triangles that are coplanar
 * (within the caller's maximum dihedral angle — strictly, or widened for
 * surveyed data whose facets carry continuous angular noise) and carry the
 * same source surface type.
 * <p>
 * The surface-type guard keeps semantic coplanar boundaries: a door or
 * window polygon abutting its wall edge-to-edge in the same plane has a
 * different surface type on each side, so its outline survives. Feature
 * isolation needs no explicit check — meshes are built per feature, so
 * triangles of different features never share a mesh. Edges with more than
 * two incident triangles (double-sided or non-manifold geometry) are left
 * untouched.
 * <p>
 * Coplanarity is evaluated in METERS: the caller supplies the same
 * degree-to-meter scale factors the T-junction pass uses (identity for
 * template-local meshes). Testing on the raw lon/lat-degree coordinates
 * would be catastrophically wrong — the ~1e5 horizontal compression turns
 * every steep surface into a near-vertical sheet, so the two slopes of a
 * gable roof end up with near-parallel raw normals and their RIDGE would be
 * merged away as "coplanar".
 * <p>
 * Edges are matched by exact vertex position (bit-level double equality,
 * collision-free by construction), not by index: abutting source polygons
 * are triangulated with disjoint vertex ranges but share coordinates
 * bit-for-bit. Coplanarity ignores winding — the pipeline renders
 * double-sided, so a flipped neighbour still subdivides the same plane.
 * Run after T-junction resolution and duplicate removal; edges that stayed
 * non-conforming (e.g. on meshes above the T-junction pass's size ceiling)
 * simply do not match and conservatively keep their outline.
 * <p>
 * There is deliberately no triangle-count ceiling: unlike the T-junction
 * pass this is a linear single pass, and its edge grouping uses counting
 * sort into per-vertex buckets (plain primitive arrays, ~40 bytes per
 * triangle) precisely so that million-triangle TIN features — where the
 * wireframe noise is worst — can be merged.
 * <p>
 * Package-private companion of {@link TriangleMesh}; the public entry point
 * is {@link TriangleMesh#mergeCoplanarOutlineEdges}, which delegates here.
 */
final class OutlineEdgeMerger {
    // Noise floor of the coplanarity test: minimum |cos| of the dihedral
    // angle (~0.08 degrees) applied when the caller requests strict
    // coplanarity (max angle 0) — it absorbs floating-point and CRS-transform
    // noise on exactly-coplanar source data. A caller-supplied max angle
    // widens the acceptance for surveyed data whose facet noise is
    // continuous (see the maxAngleDegrees parameter of merge).
    private static final double STRICT_MIN_ABS_DOT = 1.0 - 1e-6;

    /** Bit-exact position identity — full-key equality, no hash truncation. */
    private record PositionKey(long x, long y, long z) {
        static PositionKey of(double[] pos) {
            return new PositionKey(Double.doubleToLongBits(pos[0]),
                    Double.doubleToLongBits(pos[1]), Double.doubleToLongBits(pos[2]));
        }
    }

    private OutlineEdgeMerger() {
    }

    /**
     * Merge coplanar same-type neighbour edges on {@code mesh} in place;
     * {@code scaleX}/{@code scaleY} convert the mesh's X/Y units to meters
     * for the coplanarity test, {@code maxAngleDegrees} widens it beyond the
     * strict noise floor (0 = exactly coplanar only). See
     * {@link TriangleMesh#mergeCoplanarOutlineEdges} for the contract.
     */
    static void merge(TriangleMesh mesh, double scaleX, double scaleY, double maxAngleDegrees) {
        double minAbsDot = Math.min(STRICT_MIN_ABS_DOT,
                Math.cos(Math.toRadians(maxAngleDegrees)));
        int triCount = mesh.getTriangleCount();
        if (triCount < 2) {
            return;
        }
        List<int[]> tris = mesh.getTriangles();
        List<double[]> positions = mesh.getPositions();

        // Canonical vertex ids by bit-exact position: vertices of abutting
        // polygons share coordinates but not indices. Full-key equality is
        // required — a truncating 64-bit hash folds distinct grid-aligned
        // coordinates (e.g. 0.0 vs 2.0 under *31 mixing) onto one edge key,
        // and the merged edge would be skipped as non-manifold.
        Map<PositionKey, Integer> canonicalIds = new HashMap<>(mesh.getVertexCount() * 2);
        int[] canonical = new int[mesh.getVertexCount()];
        for (int v = 0; v < canonical.length; v++) {
            canonical[v] = canonicalIds.computeIfAbsent(
                    PositionKey.of(positions.get(v)), k -> canonicalIds.size());
        }
        int uniqueCount = canonicalIds.size();

        // Group the 3*T half-edges into per-vertex buckets by their smaller
        // canonical endpoint (counting sort): bucket sizes are vertex
        // degrees, so pairing inside a bucket only has to match the other
        // endpoint. Plain primitive arrays — no boxing, linear memory.
        int edgeCount = triCount * 3;
        int[] start = new int[uniqueCount + 1];
        for (int t = 0; t < triCount; t++) {
            int[] tri = tris.get(t);
            int c0 = canonical[tri[0]];
            int c1 = canonical[tri[1]];
            int c2 = canonical[tri[2]];
            start[Math.min(c0, c1) + 1]++;
            start[Math.min(c1, c2) + 1]++;
            start[Math.min(c2, c0) + 1]++;
        }
        for (int v = 0; v < uniqueCount; v++) {
            start[v + 1] += start[v];
        }
        // One packed long per half-edge, bucket-contiguous: the other
        // (larger) endpoint in the high half, the (triangle << 2 | slot)
        // provenance in the low half. Both are non-negative ints, so
        // sorting a bucket slice numerically orders it by other endpoint —
        // Arrays.sort on the primitive slice keeps high-degree buckets
        // (BIM cone/cylinder apex fans) at O(d log d).
        long[] halfEdges = new long[edgeCount];
        int[] cursor = start.clone();
        for (int t = 0; t < triCount; t++) {
            int[] tri = tris.get(t);
            int c0 = canonical[tri[0]];
            int c1 = canonical[tri[1]];
            int c2 = canonical[tri[2]];
            fillHalfEdge(cursor, halfEdges, c0, c1, t, 0);
            fillHalfEdge(cursor, halfEdges, c1, c2, t, 1);
            fillHalfEdge(cursor, halfEdges, c2, c0, t, 2);
        }

        double[][] normalCache = new double[triCount][];
        for (int v = 0; v < uniqueCount; v++) {
            int from = start[v];
            int to = start[v + 1];
            Arrays.sort(halfEdges, from, to);
            // Each run of equal other-endpoints (high halves) is one
            // undirected edge; only runs of exactly two uses may merge.
            for (int i = from; i < to; ) {
                long endpoint = halfEdges[i] >>> 32;
                int j = i + 1;
                while (j < to && halfEdges[j] >>> 32 == endpoint) {
                    j++;
                }
                if (j - i == 2) {
                    tryMerge(mesh, tris, positions, scaleX, scaleY, minAbsDot, normalCache,
                            (int) halfEdges[i], (int) halfEdges[i + 1]);
                }
                i = j;
            }
        }
    }

    private static void fillHalfEdge(int[] cursor, long[] halfEdges,
                                     int a, int b, int triIndex, int slot) {
        int lo = Math.min(a, b);
        halfEdges[cursor[lo]++] = ((long) Math.max(a, b) << 32) | ((triIndex << 2) | slot);
    }

    /** Apply the merge rule to one edge shared by exactly two half-edges. */
    private static void tryMerge(TriangleMesh mesh, List<int[]> tris, List<double[]> positions,
                                 double scaleX, double scaleY, double minAbsDot,
                                 double[][] normalCache, int firstUse, int secondUse) {
        int firstTri = firstUse >>> 2;
        int secondTri = secondUse >>> 2;
        if (firstTri == secondTri) {
            return;
        }
        byte firstBit = (byte) (1 << (firstUse & 3));
        byte secondBit = (byte) (1 << (secondUse & 3));
        byte firstMask = mesh.getTriangleOutlineEdges(firstTri);
        byte secondMask = mesh.getTriangleOutlineEdges(secondTri);
        if ((firstMask & firstBit) == 0 && (secondMask & secondBit) == 0) {
            return;
        }
        if (!Objects.equals(mesh.getTriangleSurfaceType(firstTri),
                mesh.getTriangleSurfaceType(secondTri))) {
            return;
        }
        double[] n1 = normalOf(firstTri, tris, positions, scaleX, scaleY, normalCache);
        double[] n2 = normalOf(secondTri, tris, positions, scaleX, scaleY, normalCache);
        if (n1 == null || n2 == null) {
            // Degenerate triangle — no plane to compare against.
            return;
        }
        double dot = n1[0] * n2[0] + n1[1] * n2[1] + n1[2] * n2[2];
        if (Math.abs(dot) < minAbsDot) {
            return;
        }
        mesh.setTriangleOutlineEdges(firstTri, (byte) (firstMask & ~firstBit));
        mesh.setTriangleOutlineEdges(secondTri, (byte) (secondMask & ~secondBit));
    }

    /**
     * Unit geometric normal of the triangle in metric space (X/Y scaled to
     * meters), computed once and cached; {@code null} for degenerate
     * triangles.
     */
    private static double[] normalOf(int triIndex, List<int[]> tris, List<double[]> positions,
                                     double scaleX, double scaleY, double[][] cache) {
        double[] normal = cache[triIndex];
        if (normal != null) {
            return normal.length == 3 ? normal : null;
        }
        int[] tri = tris.get(triIndex);
        double[] a = positions.get(tri[0]);
        double[] b = positions.get(tri[1]);
        double[] c = positions.get(tri[2]);
        double ux = (b[0] - a[0]) * scaleX, uy = (b[1] - a[1]) * scaleY, uz = b[2] - a[2];
        double vx = (c[0] - a[0]) * scaleX, vy = (c[1] - a[1]) * scaleY, vz = c[2] - a[2];
        double nx = uy * vz - uz * vy;
        double ny = uz * vx - ux * vz;
        double nz = ux * vy - uy * vx;
        double length = Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (length < 1e-30) {
            cache[triIndex] = new double[0];
            return null;
        }
        normal = new double[]{nx / length, ny / length, nz / length};
        cache[triIndex] = normal;
        return normal;
    }
}
