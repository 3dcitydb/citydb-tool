/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.vis.geometry;

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
    private static final double METERS_PER_DEGREE_LAT = 111_320.0;

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
        double scaleX = METERS_PER_DEGREE_LAT * Math.cos(Math.toRadians(centerY));
        double scaleY = METERS_PER_DEGREE_LAT;
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

    /** Check whether two Float32 position vectors are bitwise identical. */
    public static boolean positionsEqual(float[] a, float[] b) {
        return a[0] == b[0] && a[1] == b[1] && a[2] == b[2];
    }

    private static long gridKey(int x, int y, int z) {
        return ((long) x * 73856093L) ^ ((long) y * 19349669L) ^ ((long) z * 83492791L);
    }
}
