/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences (HFT Stuttgart) <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.util;

/**
 * Utilities for accumulating 3D axis-aligned bounding boxes.
 * <p>
 * An AABB is stored as a {@code double[6]} in the layout
 * {@code [minX, minY, minZ, maxX, maxY, maxZ]} — the same layout used
 * throughout the vis module ({@link org.citydb.vis.scene.BoundingVolume},
 * {@link org.citydb.vis.store.SpatialEntry}, node extent arrays).
 */
public class BoundingBoxUtils {
    private BoundingBoxUtils() {
    }

    /**
     * Create an empty accumulator whose mins are {@code +∞} and maxes are
     * {@code −∞}, so that the first {@code expand*} call produces a valid
     * box.
     */
    public static double[] emptyAabb() {
        return new double[]{
                Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE,
                -Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE
        };
    }

    /**
     * Grow {@code acc} to include the point {@code (x, y, z)}.
     */
    public static void expandToPoint(double[] acc, double x, double y, double z) {
        if (x < acc[0]) acc[0] = x;
        if (y < acc[1]) acc[1] = y;
        if (z < acc[2]) acc[2] = z;
        if (x > acc[3]) acc[3] = x;
        if (y > acc[4]) acc[4] = y;
        if (z > acc[5]) acc[5] = z;
    }

    /**
     * Grow {@code acc} to include another box {@code other} in the same
     * {@code [minX,minY,minZ,maxX,maxY,maxZ]} layout.
     */
    public static void expandToBox(double[] acc, double[] other) {
        if (other[0] < acc[0]) acc[0] = other[0];
        if (other[1] < acc[1]) acc[1] = other[1];
        if (other[2] < acc[2]) acc[2] = other[2];
        if (other[3] > acc[3]) acc[3] = other[3];
        if (other[4] > acc[4]) acc[4] = other[4];
        if (other[5] > acc[5]) acc[5] = other[5];
    }
}
