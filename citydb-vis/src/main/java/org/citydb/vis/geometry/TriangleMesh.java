/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.vis.geometry;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
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
        return positions;
    }

    public List<float[]> getNormals() {
        return normals;
    }

    public List<int[]> getTriangles() {
        return triangles;
    }

    public List<float[]> getTexCoords() {
        return texCoords;
    }

    public boolean hasTexCoords() {
        return hasTexCoords;
    }

    public TriangleMesh setHasTexCoords(boolean hasTexCoords) {
        this.hasTexCoords = hasTexCoords;
        return this;
    }

    public List<Long> getFeatureIds() {
        return featureIds;
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
        texCoords.add(new float[]{0f, 0f});
        return index;
    }

    public int addVertex(double x, double y, double z, float nx, float ny, float nz,
                         float u, float v) {
        int index = positions.size();
        positions.add(new double[]{x, y, z});
        normals.add(new float[]{nx, ny, nz});
        texCoords.add(new float[]{u, v});
        hasTexCoords = true;
        return index;
    }

    public void addTriangle(int v0, int v1, int v2, long featureId, int textureId) {
        triangles.add(new int[]{v0, v1, v2});
        featureIds.add(featureId);
        triangleTextureIds.add(textureId);
    }

    public List<Integer> getTriangleTextureIds() {
        return triangleTextureIds;
    }

    public void merge(TriangleMesh other) {
        int offset = positions.size();
        positions.addAll(other.positions);
        normals.addAll(other.normals);
        texCoords.addAll(other.texCoords);
        if (other.hasTexCoords) {
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
     * Remove duplicate triangles that occupy the same space (same vertex positions
     * regardless of winding order). Duplicates cause Z-fighting artifacts.
     */
    public void removeDuplicateTriangles() {
        if (triangles.size() <= 1) return;

        Set<String> seen = new HashSet<>();
        List<int[]> kept = new ArrayList<>();
        List<Long> keptIds = new ArrayList<>();
        List<Integer> keptTexIds = new ArrayList<>();

        for (int i = 0; i < triangles.size(); i++) {
            int[] tri = triangles.get(i);
            String[] posStrs = {
                    Arrays.toString(positions.get(tri[0])),
                    Arrays.toString(positions.get(tri[1])),
                    Arrays.toString(positions.get(tri[2]))
            };
            Arrays.sort(posStrs);
            String key = posStrs[0] + posStrs[1] + posStrs[2];

            if (seen.add(key)) {
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

    public double[] computeBoundingBox() {
        if (positions.isEmpty()) {
            return new double[]{0, 0, 0, 0, 0, 0};
        }

        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, minZ = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;

        for (double[] pos : positions) {
            if (pos[0] < minX) minX = pos[0];
            if (pos[1] < minY) minY = pos[1];
            if (pos[2] < minZ) minZ = pos[2];
            if (pos[0] > maxX) maxX = pos[0];
            if (pos[1] > maxY) maxY = pos[1];
            if (pos[2] > maxZ) maxZ = pos[2];
        }

        return new double[]{minX, minY, minZ, maxX, maxY, maxZ};
    }

    public double[] computeCenter() {
        double[] bbox = computeBoundingBox();
        return new double[]{
                (bbox[0] + bbox[3]) / 2,
                (bbox[1] + bbox[4]) / 2,
                (bbox[2] + bbox[5]) / 2
        };
    }
}
