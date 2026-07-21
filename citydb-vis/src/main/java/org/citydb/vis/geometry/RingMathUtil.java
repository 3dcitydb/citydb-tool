/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.geometry;

import org.citydb.model.appearance.TextureCoordinate;
import org.citydb.model.geometry.Coordinate;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared 2D projection math and ring conversions for the triangulation
 * pipeline ({@link PolygonTriangulator}, {@link HoleBridger},
 * {@link EarClipper}). All planar tests run in the polygon's dominant 2D
 * projection ({@code projAxis} = index of the dropped coordinate axis) so
 * they are valid on any plane, including vertical walls.
 */
final class RingMathUtil {
    /** Geometric tolerance shared by every planar predicate in the pipeline. */
    static final double TOLERANCE = 1e-7;

    private RingMathUtil() {
    }

    /** Drop the {@code projAxis} coordinate, yielding the 2D projection. */
    static double[] project2D(double[] point, int projAxis) {
        return switch (projAxis) {
            case 0 -> new double[]{point[1], point[2]};
            case 1 -> new double[]{point[0], point[2]};
            default -> new double[]{point[0], point[1]};
        };
    }

    /**
     * Signed area of the ring in the projection plane; the sign encodes the
     * winding direction.
     */
    static double signedArea2D(List<double[]> ring, int projAxis) {
        double area = 0;
        int n = ring.size();
        for (int i = 0; i < n; i++) {
            double[] a = project2D(ring.get(i), projAxis);
            double[] b = project2D(ring.get((i + 1) % n), projAxis);
            area += (b[0] - a[0]) * (b[1] + a[1]);
        }
        return area;
    }

    static boolean coincident2D(double[] a, double[] b) {
        return Math.abs(a[0] - b[0]) < TOLERANCE && Math.abs(a[1] - b[1]) < TOLERANCE;
    }

    /** Scale a ring's XY to meters (Z passes through unchanged). */
    static List<double[]> scaleRing(List<double[]> ring, double scaleX, double scaleY) {
        List<double[]> scaled = new ArrayList<>(ring.size());
        for (double[] pt : ring) {
            scaled.add(new double[]{pt[0] * scaleX, pt[1] * scaleY, pt[2]});
        }
        return scaled;
    }

    static List<double[]> toDoubleArray(List<Coordinate> coordinates) {
        List<double[]> result = new ArrayList<>(coordinates.size());
        for (Coordinate c : coordinates) {
            result.add(new double[]{c.getX(), c.getY(), c.getZ()});
        }
        return result;
    }

    static List<float[]> toUVArray(List<TextureCoordinate> texCoords) {
        List<float[]> result = new ArrayList<>(texCoords.size());
        for (TextureCoordinate tc : texCoords) {
            // CityGML: T=0 at bottom (OGC convention).
            // glTF (I3S / 3D Tiles): V=0 at top. Flip V axis.
            result.add(new float[]{tc.getS(), 1.0f - tc.getT()});
        }
        return result;
    }
}
