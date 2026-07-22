/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.geometry;

import org.citydb.model.common.Name;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

/**
 * Resolves T-junction vertices in a {@link TriangleMesh} by splitting affected
 * triangle edges. A T-junction occurs when a vertex lies on an edge of another
 * triangle without being a vertex of that triangle, causing sub-pixel rendering
 * cracks along the shared boundary.
 * <p>
 * Uses a uniform 3D hash grid ({@link TJunctionEdgeGrid}) over triangle edges
 * so each vertex tests only the candidate edges in its local cell rather than
 * every triangle in the mesh. Complexity is O(T) to build the index plus
 * O(V·k) for the scan, where k is the average bucket size — small for typical
 * building geometry.
 * <p>
 * Package-private companion of {@link TriangleMesh}; the public entry point is
 * {@link TriangleMesh#resolveTJunctions}, which delegates here.
 */
final class TJunctionResolver {
    private static final Logger logger = LoggerFactory.getLogger(TJunctionResolver.class);

    // Ceiling on triangle count for the T-junction pass. Below it the
    // spatial-index detection plus the split-application loop finish in
    // milliseconds for normal city-scale features. Above it (BIM-scale
    // meshes from deeply nested feature trees) the dense overlapping
    // geometry both makes detection scan minutes long and produces splits
    // that would weld topologically independent components — the wrong
    // thing to do. Skip with a warning. Package-private so the public
    // TriangleMesh#resolveTJunctions javadoc can reference it via {@value}.
    static final int MAX_TRIANGLES = 100_000;
    // Each pass may create new T-junctions at freshly inserted split
    // vertices; iterate until a pass finds none, bounded as a safety net.
    private static final int MAX_ITERATIONS = 5;

    private final TriangleMesh mesh;
    private final double scaleX;
    private final double scaleY;
    private final double toleranceMeters;
    private final double tol2;

    // Unmodifiable views over the mesh's live vertex lanes — reads observe
    // vertices appended by insertSplitVertex during the pass.
    private final List<double[]> positions;
    private final List<float[]> normals;
    private final List<float[]> texCoords;
    private final List<float[]> colors;

    /**
     * One detected T-junction: vertex {@code vertex} lies on edge
     * {@code edgeSlot} (0-2) of triangle {@code triIndex}, at parametric
     * position {@code t} along the edge — kept for correct UV/color
     * interpolation when the edge is split.
     */
    private record Split(int triIndex, int edgeSlot, int vertex, double t) {}

    private TJunctionResolver(TriangleMesh mesh, double scaleX, double scaleY,
                              double toleranceMeters) {
        this.mesh = mesh;
        this.scaleX = scaleX;
        this.scaleY = scaleY;
        this.toleranceMeters = toleranceMeters;
        tol2 = toleranceMeters * toleranceMeters;
        positions = mesh.getPositions();
        normals = mesh.getNormals();
        texCoords = mesh.getTexCoords();
        colors = mesh.getColors();
    }

    /**
     * Resolve T-junctions on {@code mesh} in place. See
     * {@link TriangleMesh#resolveTJunctions} for parameter semantics.
     */
    static void resolve(TriangleMesh mesh, double scaleX, double scaleY,
                        double toleranceMeters) {
        if (mesh.getVertexCount() < 3 || mesh.isEmpty()) {
            return;
        }

        if (mesh.getTriangleCount() > MAX_TRIANGLES) {
            logger.warn("Skipping T-junction resolution for oversized feature "
                    + "mesh (triangles={} > {}). Sub-pixel cracks at shared "
                    + "edges (if any) will not be resolved.",
                    mesh.getTriangleCount(), MAX_TRIANGLES);
            return;
        }

        new TJunctionResolver(mesh, scaleX, scaleY, toleranceMeters).run();
    }

    private void run() {
        for (int iter = 0; iter < MAX_ITERATIONS; iter++) {
            double[][] mPos = scaledPositions();
            // View over the current TriangleData's vertex lane. Re-fetched
            // every iteration: applySplits swaps a fresh TriangleData into
            // the mesh, which detaches any view taken before the swap.
            List<int[]> tris = mesh.getTriangles();

            List<Split> splits = findSplits(mPos, tris);
            if (splits.isEmpty()) {
                break;
            }

            applySplits(splits);
        }
    }

    /** Vertex positions scaled to meters (X/Y are stored in degrees). */
    private double[][] scaledPositions() {
        int vertexCount = positions.size();
        double[][] mPos = new double[vertexCount][3];
        for (int i = 0; i < vertexCount; i++) {
            double[] p = positions.get(i);
            mPos[i][0] = p[0] * scaleX;
            mPos[i][1] = p[1] * scaleY;
            mPos[i][2] = p[2];
        }
        return mPos;
    }

    /**
     * Find all T-junctions: vertex {@code vi} lies on an edge of triangle
     * {@code ti} without being one of its vertices. Each vertex only tests
     * the candidate edges in its local grid cells.
     */
    private List<Split> findSplits(double[][] mPos, List<int[]> tris) {
        int vertexCount = mPos.length;
        int triCount = tris.size();
        TJunctionEdgeGrid edgeGrid = TJunctionEdgeGrid.build(mPos, tris, triCount);

        List<Split> splits = new ArrayList<>();

        // Per-vertex dedup of edges already tested in this vertex's cell
        // sweep (the same edge can appear in multiple adjacent cells, and
        // shared edges across triangles appear under different edgeIds).
        // Stamping (increment the marker once per vertex, compare against
        // it) costs O(1) per check and avoids the per-vertex clear that
        // dominates BitSet/HashSet alternatives on BIM-scale meshes.
        int[] visitedStamp = new int[triCount * 3];
        int currentStamp = 0;

        for (int vi = 0; vi < vertexCount; vi++) {
            double vx = mPos[vi][0], vy = mPos[vi][1], vz = mPos[vi][2];
            int cxMin = TJunctionEdgeGrid.cellOf(vx - toleranceMeters);
            int cxMax = TJunctionEdgeGrid.cellOf(vx + toleranceMeters);
            int cyMin = TJunctionEdgeGrid.cellOf(vy - toleranceMeters);
            int cyMax = TJunctionEdgeGrid.cellOf(vy + toleranceMeters);
            int czMin = TJunctionEdgeGrid.cellOf(vz - toleranceMeters);
            int czMax = TJunctionEdgeGrid.cellOf(vz + toleranceMeters);

            currentStamp++;
            for (int cx = cxMin; cx <= cxMax; cx++) {
                for (int cy = cyMin; cy <= cyMax; cy++) {
                    for (int cz = czMin; cz <= czMax; cz++) {
                        TJunctionEdgeGrid.IntList bucket = edgeGrid.bucket(cx, cy, cz);
                        if (bucket == null) continue;
                        for (int bi = 0; bi < bucket.size; bi++) {
                            int edgeId = bucket.data[bi];
                            if (visitedStamp[edgeId] == currentStamp) continue;
                            visitedStamp[edgeId] = currentStamp;

                            int ti = edgeId / 3;
                            int e = edgeId % 3;
                            int[] tri = tris.get(ti);
                            if (vi == tri[0] || vi == tri[1] || vi == tri[2]) continue;

                            int ei1 = tri[e];
                            int ei2 = tri[(e + 1) % 3];
                            double dx = mPos[ei2][0] - mPos[ei1][0];
                            double dy = mPos[ei2][1] - mPos[ei1][1];
                            double dz = mPos[ei2][2] - mPos[ei1][2];
                            double edgeLen2 = dx * dx + dy * dy + dz * dz;
                            if (edgeLen2 < 1e-10) continue;

                            double t = ((vx - mPos[ei1][0]) * dx
                                    + (vy - mPos[ei1][1]) * dy
                                    + (vz - mPos[ei1][2]) * dz) / edgeLen2;
                            if (t <= 0.001 || t >= 0.999) continue;

                            double px = mPos[ei1][0] + t * dx;
                            double py = mPos[ei1][1] + t * dy;
                            double pz = mPos[ei1][2] + t * dz;
                            double dist2 = (vx - px) * (vx - px)
                                    + (vy - py) * (vy - py)
                                    + (vz - pz) * (vz - pz);

                            if (dist2 < tol2) {
                                splits.add(new Split(ti, e, vi, t));
                            }
                        }
                    }
                }
            }
        }
        return splits;
    }

    /**
     * Split each affected triangle into two children at its T-junction
     * vertex, then rebuild the mesh's triangle lanes: surviving triangles
     * in original order, followed by the split children.
     */
    private void applySplits(List<Split> splits) {
        TriangleMesh.TriangleData data = mesh.triangleData();
        int triCount = data.size();

        // BitSet (primitive bit-per-triangle) rather than HashSet<Integer>
        // — splits can hold millions of entries on dense meshes near the
        // size threshold, and the per-entry Integer.box + HashMap.contains
        // cost dominated the loop in profiling.
        BitSet removed = new BitSet(triCount);
        // Sub-triangles produced by the splits. Each inherits its parent
        // triangle's attributes (featureId/textureId/colored/surfaceType)
        // — a single split never crosses a surface boundary — which
        // TriangleData.add carries across in lockstep. The outline-edge mask
        // is the exception: it is per-edge, so the split remaps it explicitly
        // below instead of copying it wholesale.
        TriangleMesh.TriangleData splitChildren = new TriangleMesh.TriangleData();

        for (Split split : splits) {
            int ti = split.triIndex();
            if (removed.get(ti)) continue;

            int[] tri = data.vertices(ti);
            int e = split.edgeSlot();
            int ei1 = tri[e];
            int ei2 = tri[(e + 1) % 3];
            int ei3 = tri[(e + 2) % 3];
            long fid = data.featureId(ti);
            int texId = data.textureId(ti);
            boolean colored = data.isColored(ti);
            Name surfaceType = data.surfaceType(ti);

            // Outline-edge inheritance: rotate the parent's mask into the
            // split edge's frame — bitA is the split edge itself (slot e),
            // bitB the following edge ei2->ei3, bitC the preceding ei3->ei1.
            // Both halves of the split edge keep bitA; the internal edge
            // newVi<->ei3 introduced by the split is never a boundary edge.
            byte outline = data.outlineEdges(ti);
            boolean bitA = (outline & (1 << e)) != 0;
            boolean bitB = (outline & (1 << ((e + 1) % 3))) != 0;
            boolean bitC = (outline & (1 << ((e + 2) % 3))) != 0;
            // child1 = (ei1, newVi, ei3): edges ei1->newVi (bitA),
            // newVi->ei3 (internal), ei3->ei1 (bitC).
            byte child1Outline = (byte) ((bitA ? TriangleMesh.OUTLINE_EDGE_01 : 0)
                    | (bitC ? TriangleMesh.OUTLINE_EDGE_20 : 0));
            // child2 = (newVi, ei2, ei3): edges newVi->ei2 (bitA),
            // ei2->ei3 (bitB), ei3->newVi (internal).
            byte child2Outline = (byte) ((bitA ? TriangleMesh.OUTLINE_EDGE_01 : 0)
                    | (bitB ? TriangleMesh.OUTLINE_EDGE_12 : 0));

            int newVi = insertSplitVertex(split.vertex(), ei1, ei2, (float) split.t());

            removed.set(ti);
            splitChildren.add(new int[]{ei1, newVi, ei3}, fid, texId, colored, surfaceType,
                    child1Outline);
            splitChildren.add(new int[]{newVi, ei2, ei3}, fid, texId, colored, surfaceType,
                    child2Outline);
        }

        // Rebuild: surviving triangles in original order, then the split
        // children. TriangleData keeps all six attribute lanes aligned,
        // so this is a copy of the kept indices plus an append of the
        // children — no manual per-lane re-synchronisation.
        TriangleMesh.TriangleData rebuilt = new TriangleMesh.TriangleData();
        for (int ti = 0; ti < triCount; ti++) {
            if (!removed.get(ti)) {
                rebuilt.addCopy(data.vertices(ti), data, ti);
            }
        }
        rebuilt.addAll(splitChildren);
        mesh.replaceTriangleData(rebuilt);
    }

    /**
     * Add a new vertex at {@code vi}'s position with the split triangle's
     * normal, interpolating UV/color along the split edge {@code ei1→ei2}
     * at the parametric position {@code t}.
     * <p>
     * Assumes per-face normals (all edge vertices share the same normal, as
     * emitted by {@link PolygonTriangulator}). If upstream ever produces
     * per-vertex smooth normals, this should interpolate.
     */
    private int insertSplitVertex(int vi, int ei1, int ei2, float t) {
        float[] triNormal = normals.get(ei1);
        double[] viPos = positions.get(vi);
        if (mesh.hasTexCoords() && mesh.hasColors()) {
            float[] uv1 = texCoords.get(ei1), uv2 = texCoords.get(ei2);
            float[] c1 = colors.get(ei1), c2 = colors.get(ei2);
            return mesh.addVertex(viPos[0], viPos[1], viPos[2],
                    triNormal[0], triNormal[1], triNormal[2],
                    uv1[0] + t * (uv2[0] - uv1[0]),
                    uv1[1] + t * (uv2[1] - uv1[1]),
                    c1[0] + t * (c2[0] - c1[0]),
                    c1[1] + t * (c2[1] - c1[1]),
                    c1[2] + t * (c2[2] - c1[2]),
                    c1[3] + t * (c2[3] - c1[3]));
        } else if (mesh.hasTexCoords()) {
            float[] uv1 = texCoords.get(ei1), uv2 = texCoords.get(ei2);
            return mesh.addVertex(viPos[0], viPos[1], viPos[2],
                    triNormal[0], triNormal[1], triNormal[2],
                    uv1[0] + t * (uv2[0] - uv1[0]),
                    uv1[1] + t * (uv2[1] - uv1[1]));
        } else if (mesh.hasColors()) {
            float[] c1 = colors.get(ei1), c2 = colors.get(ei2);
            return mesh.addVertex(viPos[0], viPos[1], viPos[2],
                    triNormal[0], triNormal[1], triNormal[2],
                    c1[0] + t * (c2[0] - c1[0]),
                    c1[1] + t * (c2[1] - c1[1]),
                    c1[2] + t * (c2[2] - c1[2]),
                    c1[3] + t * (c2[3] - c1[3]));
        } else {
            return mesh.addVertex(viPos[0], viPos[1], viPos[2],
                    triNormal[0], triNormal[1], triNormal[2]);
        }
    }
}
