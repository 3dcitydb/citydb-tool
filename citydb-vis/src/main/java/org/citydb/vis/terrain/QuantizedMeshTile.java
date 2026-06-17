/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences (HFT Stuttgart) <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.terrain;

/**
 * A decoded <a href="https://github.com/CesiumGS/quantized-mesh">quantized-mesh
 * 1.0</a> terrain tile: a triangulated height field over the tile's geographic
 * extent, ready for point height sampling.
 * <p>
 * Vertex coordinates are stored in the tile-local quantized space the format
 * uses ({@code u}, {@code v} in {@code [0, 32767]} mapping linearly onto the
 * tile's longitude/latitude extent, {@code h} in {@code [0, 32767]} mapping
 * onto {@code [minHeight, maxHeight]}). {@link #sampleHeight} converts a
 * geographic point into that local space, locates the containing triangle, and
 * barycentrically interpolates the height.
 * <p>
 * Instances are immutable after construction and therefore safe to share
 * across the writer's processing threads.
 */
final class QuantizedMeshTile {
    // Tolerance (in quantized u/v units) for accepting points on a triangle
    // edge/vertex, shared by the bounding-box quick reject and the barycentric
    // containment test so the two stay consistent.
    private static final double EDGE_TOLERANCE = 1e-6;

    private final double west;
    private final double south;
    private final double lonSpan;
    private final double latSpan;
    private final double minHeight;
    private final double heightSpan;

    // Vertex quantized coordinates, index-aligned. u/v in [0, 32767].
    private final int[] u;
    private final int[] v;
    private final int[] h;

    // Triangle vertex indices, three per triangle.
    private final int[] indices;

    QuantizedMeshTile(double west, double south, double east, double north,
                      double minHeight, double maxHeight,
                      int[] u, int[] v, int[] h, int[] indices) {
        this.west = west;
        this.south = south;
        this.lonSpan = east - west;
        this.latSpan = north - south;
        this.minHeight = minHeight;
        this.heightSpan = maxHeight - minHeight;
        this.u = u;
        this.v = v;
        this.h = h;
        this.indices = indices;
    }

    private double decodeHeight(int hq) {
        return minHeight + (hq / 32767.0) * heightSpan;
    }

    /**
     * Interpolate the terrain height (metres above the WGS84 ellipsoid) at the
     * given geographic point. Returns {@link Double#NaN} when the point falls
     * outside this tile's extent or no triangle contains it (degenerate tile).
     */
    double sampleHeight(double lon, double lat) {
        if (lonSpan <= 0 || latSpan <= 0 || indices.length == 0) {
            return Double.NaN;
        }

        // Target point in [0, 32767] quantized tile space.
        double pu = (lon - west) / lonSpan * 32767.0;
        double pv = (lat - south) / latSpan * 32767.0;

        for (int t = 0; t < indices.length; t += 3) {
            int i0 = indices[t];
            int i1 = indices[t + 1];
            int i2 = indices[t + 2];

            double ax = u[i0], ay = v[i0];
            double bx = u[i1], by = v[i1];
            double cx = u[i2], cy = v[i2];

            // Axis-aligned bounding-box quick reject: a point outside the
            // triangle's AABB cannot be inside the triangle, so skip it before
            // the costlier barycentric solve. Relaxed by EDGE_TOLERANCE to stay
            // exactly equivalent to the barycentric edge tolerance below — a
            // point accepted there is never rejected here.
            if (pu < Math.min(ax, Math.min(bx, cx)) - EDGE_TOLERANCE
                    || pu > Math.max(ax, Math.max(bx, cx)) + EDGE_TOLERANCE
                    || pv < Math.min(ay, Math.min(by, cy)) - EDGE_TOLERANCE
                    || pv > Math.max(ay, Math.max(by, cy)) + EDGE_TOLERANCE) {
                continue;
            }

            // Barycentric coordinates of (pu, pv) in triangle (a, b, c).
            double det = (by - cy) * (ax - cx) + (cx - bx) * (ay - cy);
            if (det == 0) {
                continue;
            }
            double l1 = ((by - cy) * (pu - cx) + (cx - bx) * (pv - cy)) / det;
            double l2 = ((cy - ay) * (pu - cx) + (ax - cx) * (pv - cy)) / det;
            double l3 = 1.0 - l1 - l2;

            // Small tolerance so points on shared edges still resolve.
            if (l1 >= -EDGE_TOLERANCE && l2 >= -EDGE_TOLERANCE && l3 >= -EDGE_TOLERANCE) {
                return l1 * decodeHeight(h[i0])
                        + l2 * decodeHeight(h[i1])
                        + l3 * decodeHeight(h[i2]);
            }
        }
        return Double.NaN;
    }
}
