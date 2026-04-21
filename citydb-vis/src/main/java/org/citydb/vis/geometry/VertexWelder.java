/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.geometry;

import org.citydb.vis.util.GeoTransform;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Vertex welding utility shared by all visualization format encoders.
 * <p>
 * Weld vertex positions: find vertices within 2 cm of each other and map
 * them to the same Float32 position. Uses a spatial hash grid for O(N)
 * performance instead of O(N²) brute force.
 * <p>
 * UV-aware: two vertices are only welded if they share the same position
 * AND the same UV coordinates (within tolerance). This preserves texture
 * seams where adjacent polygons meet with different UVs.
 */
public class VertexWelder {

    /**
     * Weld vertex positions using a spatial hash grid. Returns a flat array
     * of Float32 positions indexed by {@code [triIndex * 3 + vertIndex]},
     * with positions relative to the given center (already subtracted).
     *
     * @param mesh    source triangle mesh
     * @param centerX center longitude (degrees)
     * @param centerY center latitude (degrees)
     * @param centerZ center altitude (meters)
     * @return welded positions array, one entry per triangle vertex
     */
    public static float[][] weld(TriangleMesh mesh, double centerX,
                                 double centerY, double centerZ) {
        double scaleX = GeoTransform.metersPerDegreeLon(centerY);
        double scaleY = GeoTransform.WGS84_METERS_PER_DEGREE_LAT;
        float weldTolerance = 0.02f; // 2 cm
        float weldTolerance2 = weldTolerance * weldTolerance;
        boolean hasUV = mesh.hasTexCoords();
        float uvTolerance = 0.001f;

        List<double[]> positions = mesh.getPositions();
        List<float[]> texCoords = mesh.getTexCoords();
        int posCount = positions.size();

        float[][] f32 = new float[posCount][3];
        float[][] mPos = new float[posCount][3];
        for (int i = 0; i < posCount; i++) {
            double[] p = positions.get(i);
            f32[i][0] = (float) (p[0] - centerX);
            f32[i][1] = (float) (p[1] - centerY);
            f32[i][2] = (float) (p[2] - centerZ);
            mPos[i][0] = (float) ((p[0] - centerX) * scaleX);
            mPos[i][1] = (float) ((p[1] - centerY) * scaleY);
            mPos[i][2] = f32[i][2];
        }

        // Spatial hash grid: cell size = weld tolerance
        float cellSize = weldTolerance;
        Map<Long, List<Integer>> grid = new HashMap<>();
        int[] remap = new int[posCount];

        for (int i = 0; i < posCount; i++) {
            int gx = Math.round(mPos[i][0] / cellSize);
            int gy = Math.round(mPos[i][1] / cellSize);
            int gz = Math.round(mPos[i][2] / cellSize);

            remap[i] = i;
            // Search 3x3x3 neighborhood for a canonical vertex within tolerance
            outer:
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        long key = gridKey(gx + dx, gy + dy, gz + dz);
                        List<Integer> cell = grid.get(key);
                        if (cell == null) continue;
                        for (int j : cell) {
                            float ex = mPos[i][0] - mPos[j][0];
                            float ey = mPos[i][1] - mPos[j][1];
                            float ez = mPos[i][2] - mPos[j][2];
                            if (ex * ex + ey * ey + ez * ez < weldTolerance2) {
                                if (hasUV) {
                                    float[] uvI = texCoords.get(i);
                                    float[] uvJ = texCoords.get(j);
                                    if (Math.abs(uvI[0] - uvJ[0]) > uvTolerance
                                            || Math.abs(uvI[1] - uvJ[1]) > uvTolerance) {
                                        continue;
                                    }
                                }
                                remap[i] = j;
                                break outer;
                            }
                        }
                    }
                }
            }

            if (remap[i] == i) {
                long key = gridKey(gx, gy, gz);
                grid.computeIfAbsent(key, k -> new ArrayList<>()).add(i);
            }
        }

        int triCount = mesh.getTriangleCount();
        float[][] result = new float[triCount * 3][];
        int vi = 0;
        for (int[] tri : mesh.getTriangles()) {
            for (int idx : tri) {
                result[vi++] = f32[remap[idx]];
            }
        }
        return result;
    }

    // ---- Post-weld processing ------------------------------------------------

    /**
     * Result of welding vertices, filtering degenerate triangles, and
     * computing face ranges. Shared by all format-specific encoders.
     */
    public record WeldResult(float[][] weldedPositions,
                             List<Integer> validTriIndices,
                             List<int[]> faceRanges,
                             List<Long> rangeFeatureIds,
                             int vertexCount) {
        public boolean isEmpty() {
            return vertexCount == 0;
        }

        /**
         * Compute per-vertex feature indices from face ranges.
         * Each vertex in a face range gets the range's sequential index.
         */
        public int[] computeFeatureIndices() {
            int[] indices = new int[vertexCount];
            for (int f = 0; f < faceRanges.size(); f++) {
                int[] range = faceRanges.get(f);
                for (int t = range[0]; t <= range[1]; t++) {
                    indices[t * 3] = f;
                    indices[t * 3 + 1] = f;
                    indices[t * 3 + 2] = f;
                }
            }
            return indices;
        }
    }

    /**
     * Weld vertices, filter degenerate triangles, and compute face ranges
     * with per-range featureId tracking.
     */
    public static WeldResult weldAndFilter(TriangleMesh mesh,
                                           double centerX, double centerY,
                                           double centerZ) {
        float[][] weldedPositions = weld(mesh, centerX, centerY, centerZ);

        List<int[]> allTriangles = mesh.getTriangles();
        List<Long> triFeatureIds = mesh.getFeatureIds();
        List<Integer> validTriIndices = new ArrayList<>();
        int vi = 0;
        for (int t = 0; t < allTriangles.size(); t++) {
            float[] p0 = weldedPositions[vi], p1 = weldedPositions[vi + 1],
                    p2 = weldedPositions[vi + 2];
            if (!positionsEqual(p0, p1) && !positionsEqual(p0, p2)
                    && !positionsEqual(p1, p2)) {
                validTriIndices.add(t);
            }
            vi += 3;
        }

        int vertexCount = validTriIndices.size() * 3;
        if (vertexCount == 0) {
            return new WeldResult(weldedPositions, validTriIndices,
                    List.of(), List.of(), 0);
        }

        List<int[]> faceRanges = new ArrayList<>();
        List<Long> rangeFeatureIds = new ArrayList<>();
        int start = 0;
        long currentId = triFeatureIds.get(validTriIndices.get(0));
        rangeFeatureIds.add(currentId);
        for (int i = 1; i < validTriIndices.size(); i++) {
            long id = triFeatureIds.get(validTriIndices.get(i));
            if (id != currentId) {
                faceRanges.add(new int[]{start, i - 1});
                start = i;
                currentId = id;
                rangeFeatureIds.add(currentId);
            }
        }
        faceRanges.add(new int[]{start, validTriIndices.size() - 1});

        return new WeldResult(weldedPositions, validTriIndices,
                faceRanges, rangeFeatureIds, vertexCount);
    }

    /** Check whether two Float32 position vectors are bitwise identical. */
    public static boolean positionsEqual(float[] a, float[] b) {
        return a[0] == b[0] && a[1] == b[1] && a[2] == b[2];
    }

    /**
     * Callback for {@link #iterateOutputVertices}. Invoked once per output
     * vertex in triangle-soup order.
     */
    @FunctionalInterface
    public interface VertexVisitor {
        /**
         * @param outIdx        sequential output vertex index (0..vertexCount-1)
         * @param weldedPos     welded Float32 position {@code [x, y, z]} in
         *                      node-local (centered, unscaled) coordinates
         * @param sourceVertIdx original mesh vertex index for looking up
         *                      per-vertex attributes (normals, UVs, ...)
         */
        void visit(int outIdx, float[] weldedPos, int sourceVertIdx);
    }

    /**
     * Iterate over all output vertices (valid triangles only, in triangle-soup
     * order) and invoke {@code visitor} with the welded position and the
     * source vertex index. Encapsulates the per-encoder boilerplate of
     * walking {@code validTriIndices} × 3.
     */
    public static void iterateOutputVertices(WeldResult weld, TriangleMesh mesh,
                                             VertexVisitor visitor) {
        float[][] weldedPositions = weld.weldedPositions();
        List<int[]> allTriangles = mesh.getTriangles();
        int idx = 0;
        for (int ti : weld.validTriIndices()) {
            int base = ti * 3;
            int[] tri = allTriangles.get(ti);
            for (int j = 0; j < 3; j++) {
                visitor.visit(idx++, weldedPositions[base + j], tri[j]);
            }
        }
    }

    private static long gridKey(int x, int y, int z) {
        return ((long) x * 73856093L) ^ ((long) y * 19349669L) ^ ((long) z * 83492791L);
    }
}
