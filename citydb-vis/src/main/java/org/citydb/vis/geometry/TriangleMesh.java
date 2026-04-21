/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.geometry;

import org.citydb.vis.util.BoundingBoxUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class TriangleMesh {
    private final List<double[]> positions;
    private final List<float[]> normals;
    private final List<float[]> texCoords;
    private final List<int[]> triangles;
    private final List<Long> featureIds;
    private final List<Integer> triangleTextureIds;
    private boolean hasTexCoords;

    public TriangleMesh() {
        positions = new ArrayList<>();
        normals = new ArrayList<>();
        texCoords = new ArrayList<>();
        triangles = new ArrayList<>();
        featureIds = new ArrayList<>();
        triangleTextureIds = new ArrayList<>();
    }

    public List<double[]> getPositions() {
        return Collections.unmodifiableList(positions);
    }

    public List<float[]> getNormals() {
        return Collections.unmodifiableList(normals);
    }

    public List<int[]> getTriangles() {
        return Collections.unmodifiableList(triangles);
    }

    public List<float[]> getTexCoords() {
        return Collections.unmodifiableList(texCoords);
    }

    public boolean hasTexCoords() {
        return hasTexCoords;
    }

    public TriangleMesh setHasTexCoords(boolean hasTexCoords) {
        this.hasTexCoords = hasTexCoords;
        return this;
    }

    public List<Long> getFeatureIds() {
        return Collections.unmodifiableList(featureIds);
    }

    public int getVertexCount() {
        return positions.size();
    }

    public int getTriangleCount() {
        return triangles.size();
    }

    public boolean isEmpty() {
        return triangles.isEmpty();
    }

    public int addVertex(double x, double y, double z, float nx, float ny, float nz) {
        int index = positions.size();
        positions.add(new double[]{x, y, z});
        normals.add(new float[]{nx, ny, nz});
        // If the mesh already has UVs, pad this vertex so the
        // texCoords.size() == positions.size() invariant holds.
        if (hasTexCoords) {
            texCoords.add(new float[]{0f, 0f});
        }
        return index;
    }

    public int addVertex(double x, double y, double z, float nx, float ny, float nz,
                         float u, float v) {
        int index = positions.size();
        positions.add(new double[]{x, y, z});
        normals.add(new float[]{nx, ny, nz});
        // First UV vertex for this mesh: backfill any earlier non-UV vertices
        // with {0,0} so the texCoords.size() == positions.size() invariant holds.
        if (!hasTexCoords) {
            while (texCoords.size() < index) {
                texCoords.add(new float[]{0f, 0f});
            }
            hasTexCoords = true;
        }
        texCoords.add(new float[]{u, v});
        return index;
    }

    public void addTriangle(int v0, int v1, int v2, long featureId, int textureId) {
        triangles.add(new int[]{v0, v1, v2});
        featureIds.add(featureId);
        triangleTextureIds.add(textureId);
    }

    public List<Integer> getTriangleTextureIds() {
        return Collections.unmodifiableList(triangleTextureIds);
    }

    public void merge(TriangleMesh other) {
        int offset = positions.size();
        positions.addAll(other.positions);
        normals.addAll(other.normals);

        // Maintain the invariant: when hasTexCoords is true,
        // texCoords.size() == positions.size(). Pad with {0,0} on either side
        // when mixing textured and untextured meshes within a single node.
        if (hasTexCoords || other.hasTexCoords) {
            while (texCoords.size() < offset) {
                texCoords.add(new float[]{0f, 0f});
            }
            if (other.hasTexCoords) {
                texCoords.addAll(other.texCoords);
            } else {
                int otherSize = other.positions.size();
                for (int i = 0; i < otherSize; i++) {
                    texCoords.add(new float[]{0f, 0f});
                }
            }
            hasTexCoords = true;
        }

        for (int[] tri : other.triangles) {
            triangles.add(new int[]{tri[0] + offset, tri[1] + offset, tri[2] + offset});
        }
        featureIds.addAll(other.featureIds);
        triangleTextureIds.addAll(other.triangleTextureIds);
    }

    /**
     * Resolve T-junction vertices by splitting affected triangle edges.
     * A T-junction occurs when a vertex lies on an edge of another triangle
     * without being a vertex of that triangle, causing sub-pixel rendering cracks.
     * <p>
     * <b>Performance:</b> this implementation is O(V * T) per iteration and is
     * intended to run only on per-feature meshes (typically &lt;1000 vertices
     * and &lt;1000 triangles). Do NOT call it on merged node-level meshes
     * without first adding a spatial index — the naive scan does not scale to
     * Bayern-sized nodes.
     *
     * @param scaleX degrees-to-meters scale for X (longitude)
     * @param scaleY degrees-to-meters scale for Y (latitude)
     * @param toleranceMeters distance threshold in meters
     */
    public void resolveTJunctions(double scaleX, double scaleY, double toleranceMeters) {
        if (positions.size() < 3 || triangles.isEmpty()) return;

        double tol2 = toleranceMeters * toleranceMeters;
        int maxIterations = 5;

        for (int iter = 0; iter < maxIterations; iter++) {
            int vertexCount = positions.size();
            double[][] mPos = new double[vertexCount][3];
            for (int i = 0; i < vertexCount; i++) {
                double[] p = positions.get(i);
                mPos[i][0] = p[0] * scaleX;
                mPos[i][1] = p[1] * scaleY;
                mPos[i][2] = p[2];
            }

            // Find all T-junctions: vertex vi lies on edge of triangle ti.
            // Store the parametric position t along the edge for correct UV interpolation.
            List<int[]> splits = new ArrayList<>();   // {triIndex, edgeSlot, tJunctionVertex}
            List<Double> splitParams = new ArrayList<>(); // parametric t for each split
            int triCount = triangles.size();

            for (int vi = 0; vi < vertexCount; vi++) {
                double vx = mPos[vi][0], vy = mPos[vi][1], vz = mPos[vi][2];

                for (int ti = 0; ti < triCount; ti++) {
                    int[] tri = triangles.get(ti);
                    if (vi == tri[0] || vi == tri[1] || vi == tri[2]) continue;

                    for (int e = 0; e < 3; e++) {
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
                            splits.add(new int[]{ti, e, vi});
                            splitParams.add(t);
                            break;
                        }
                    }
                }
            }

            if (splits.isEmpty()) break;

            Set<Integer> removed = new HashSet<>();
            List<int[]> newTriangles = new ArrayList<>();
            List<Long> newFeatureIds = new ArrayList<>();
            List<Integer> newTriTexIds = new ArrayList<>();

            for (int s = 0; s < splits.size(); s++) {
                int[] split = splits.get(s);
                int ti = split[0], edgeSlot = split[1], vi = split[2];
                if (removed.contains(ti)) continue;

                int[] tri = triangles.get(ti);
                int ei1 = tri[edgeSlot];
                int ei2 = tri[(edgeSlot + 1) % 3];
                int ei3 = tri[(edgeSlot + 2) % 3];
                long fid = featureIds.get(ti);
                int texId = triangleTextureIds.get(ti);

                // New vertex at vi's position with the split triangle's normal.
                // Interpolate UV along the edge at the parametric position t.
                // Assumes per-face normals (all edge vertices share the same
                // normal, as emitted by PolygonTriangulator). If upstream ever
                // produces per-vertex smooth normals, this should interpolate.
                float[] triNormal = normals.get(ei1);
                double[] viPos = positions.get(vi);
                int newVi;
                if (hasTexCoords) {
                    float[] uv1 = texCoords.get(ei1);
                    float[] uv2 = texCoords.get(ei2);
                    float tParam = (float) splitParams.get(s).doubleValue();
                    float interpU = uv1[0] + tParam * (uv2[0] - uv1[0]);
                    float interpV = uv1[1] + tParam * (uv2[1] - uv1[1]);
                    newVi = addVertex(viPos[0], viPos[1], viPos[2],
                            triNormal[0], triNormal[1], triNormal[2],
                            interpU, interpV);
                } else {
                    newVi = addVertex(viPos[0], viPos[1], viPos[2],
                            triNormal[0], triNormal[1], triNormal[2]);
                }

                removed.add(ti);
                newTriangles.add(new int[]{ei1, newVi, ei3});
                newFeatureIds.add(fid);
                newTriTexIds.add(texId);
                newTriangles.add(new int[]{newVi, ei2, ei3});
                newFeatureIds.add(fid);
                newTriTexIds.add(texId);
            }

            List<int[]> updatedTri = new ArrayList<>();
            List<Long> updatedFid = new ArrayList<>();
            List<Integer> updatedTexId = new ArrayList<>();
            for (int ti = 0; ti < triCount; ti++) {
                if (!removed.contains(ti)) {
                    updatedTri.add(triangles.get(ti));
                    updatedFid.add(featureIds.get(ti));
                    updatedTexId.add(triangleTextureIds.get(ti));
                }
            }
            updatedTri.addAll(newTriangles);
            updatedFid.addAll(newFeatureIds);
            updatedTexId.addAll(newTriTexIds);

            triangles.clear();
            triangles.addAll(updatedTri);
            featureIds.clear();
            featureIds.addAll(updatedFid);
            triangleTextureIds.clear();
            triangleTextureIds.addAll(updatedTexId);
        }
    }

    /**
     * Key for duplicate-triangle detection: three position hashes, compared
     * by value. Using a record gives auto-generated equals/hashCode that
     * compare all three 64-bit fields. Hash collisions in {@link #vertexHash}
     * are theoretically possible but vanishingly unlikely for real geographic
     * coordinate data (birthday-paradox threshold ~4 * 10^9 distinct vertices).
     */
    private record TriangleKey(long h0, long h1, long h2) {}

    /**
     * Remove duplicate triangles that occupy the same space (same vertex positions
     * regardless of winding order). Duplicates cause Z-fighting artifacts.
     * <p>
     * Dedup is exact on {@code Double.doubleToLongBits} — downstream vertex
     * welding in the geometry encoder handles near-duplicates within 2 cm,
     * so upstream rounding would be both redundant and incorrect.
     */
    public void removeDuplicateTriangles() {
        if (triangles.size() <= 1) return;

        Set<TriangleKey> seen = new HashSet<>();
        List<int[]> kept = new ArrayList<>();
        List<Long> keptIds = new ArrayList<>();
        List<Integer> keptTexIds = new ArrayList<>();

        for (int i = 0; i < triangles.size(); i++) {
            int[] tri = triangles.get(i);
            long h0 = vertexHash(positions.get(tri[0]));
            long h1 = vertexHash(positions.get(tri[1]));
            long h2 = vertexHash(positions.get(tri[2]));

            // Sort ascending so the key is winding-order-independent.
            if (h0 > h1) { long t = h0; h0 = h1; h1 = t; }
            if (h1 > h2) { long t = h1; h1 = h2; h2 = t; }
            if (h0 > h1) { long t = h0; h0 = h1; h1 = t; }

            if (seen.add(new TriangleKey(h0, h1, h2))) {
                kept.add(tri);
                keptIds.add(featureIds.get(i));
                keptTexIds.add(triangleTextureIds.get(i));
            }
        }

        if (kept.size() < triangles.size()) {
            triangles.clear();
            triangles.addAll(kept);
            featureIds.clear();
            featureIds.addAll(keptIds);
            triangleTextureIds.clear();
            triangleTextureIds.addAll(keptTexIds);
        }
    }

    /**
     * Hash a vertex position to a 64-bit value using exact double bits.
     * Bit-identical positions always hash equal; collisions are possible in
     * principle but statistically irrelevant for realistic coordinate data
     * (birthday-paradox threshold ~4 * 10^9 distinct vertices).
     */
    private static long vertexHash(double[] pos) {
        long h = Double.doubleToLongBits(pos[0]);
        h = h * 31L + Double.doubleToLongBits(pos[1]);
        h = h * 31L + Double.doubleToLongBits(pos[2]);
        return h;
    }

    public double[] computeBoundingBox() {
        if (positions.isEmpty()) {
            return new double[]{0, 0, 0, 0, 0, 0};
        }

        double[] acc = BoundingBoxUtils.emptyAabb();
        for (double[] pos : positions) {
            BoundingBoxUtils.expandToPoint(acc, pos[0], pos[1], pos[2]);
        }
        return acc;
    }

    public double[] computeCenter() {
        double[] bbox = computeBoundingBox();
        return new double[]{
                (bbox[0] + bbox[3]) / 2,
                (bbox[1] + bbox[4]) / 2,
                (bbox[2] + bbox[5]) / 2
        };
    }

    /**
     * Shift all vertex Z values so the mesh's bottom sits at height 0.
     */
    public void clampToGround() {
        if (positions.isEmpty()) {
            return;
        }
        double minZ = Double.MAX_VALUE;
        for (double[] pos : positions) {
            if (pos[2] < minZ) minZ = pos[2];
        }
        if (minZ != 0) {
            for (double[] pos : positions) {
                pos[2] -= minZ;
            }
        }
    }

    /**
     * Compute per-texture UV extent from triangle texture IDs and vertex UVs.
     * Returns texId → [minU, minV, maxU, maxV]. Triangles with texId &lt; 0
     * are skipped.
     */
    public Map<Integer, float[]> computeUVExtents() {
        Map<Integer, float[]> extents = new HashMap<>();
        for (int t = 0; t < triangles.size(); t++) {
            int texId = triangleTextureIds.get(t);
            if (texId < 0) {
                continue;
            }
            float[] ext = extents.computeIfAbsent(texId,
                    k -> new float[]{Float.MAX_VALUE, Float.MAX_VALUE,
                            -Float.MAX_VALUE, -Float.MAX_VALUE});
            int[] tri = triangles.get(t);
            for (int vi : tri) {
                float[] uv = texCoords.get(vi);
                ext[0] = Math.min(ext[0], uv[0]);
                ext[1] = Math.min(ext[1], uv[1]);
                ext[2] = Math.max(ext[2], uv[0]);
                ext[3] = Math.max(ext[3], uv[1]);
            }
        }
        return extents;
    }
}
