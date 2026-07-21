/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.geometry;

import java.util.ArrayList;
import java.util.List;

/**
 * Ear-clipping triangulation of a single (possibly keyhole-bridged) ring.
 * Operates on scaled coordinates (meters) in the polygon's dominant 2D
 * projection so the geometric tests are correct on every plane, including
 * vertical walls. Expects CCW winding in the projection plane; the caller
 * normalizes winding beforehand.
 * <p>
 * When no convex ear can be found (typical for the slightly self-touching
 * rings produced by keyhole bridging), a fallback accepts concave triples
 * that pass the same vertex-containment check, and as a last resort removes
 * the most-collinear vertex to simplify the ring and retry.
 * <p>
 * Package-private companion of {@link PolygonTriangulator}, which owns
 * projection-axis selection and winding normalization.
 */
final class EarClipper {
    private EarClipper() {
    }

    /**
     * Triangulate the ring, returning index triples into {@code vertices}.
     */
    static List<int[]> clip(List<double[]> vertices, int projAxis) {
        List<int[]> triangles = new ArrayList<>();
        int n = vertices.size();

        if (n < 3) return triangles;
        if (n == 3) {
            triangles.add(new int[]{0, 1, 2});
            return triangles;
        }

        List<Integer> indices = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            indices.add(i);
        }

        int attempts = 0;
        int maxAttempts = n * n;

        while (indices.size() > 2 && attempts < maxAttempts) {
            boolean earFound = false;

            for (int i = 0; i < indices.size(); i++) {
                int prevIdx = (i - 1 + indices.size()) % indices.size();
                int nextIdx = (i + 1) % indices.size();

                int prev = indices.get(prevIdx);
                int curr = indices.get(i);
                int next = indices.get(nextIdx);

                double[] a = RingMathUtil.project2D(vertices.get(prev), projAxis);
                double[] b = RingMathUtil.project2D(vertices.get(curr), projAxis);
                double[] c = RingMathUtil.project2D(vertices.get(next), projAxis);

                if (cross2D(a, b, c) <= RingMathUtil.TOLERANCE) {
                    attempts++;
                    continue;
                }

                boolean isEar = !triangleContainsOtherVertex(
                        indices, vertices, projAxis, prevIdx, i, nextIdx, a, b, c);

                if (isEar) {
                    triangles.add(new int[]{prev, curr, next});
                    indices.remove(i);
                    earFound = true;
                    break;
                }

                attempts++;
            }

            if (!earFound) {
                if (indices.size() > 2) {
                    // Fallback: find a non-degenerate triple that does NOT contain
                    // any other polygon vertex — same containment check as the
                    // normal ear path, but also accepting concave (CW) triples.
                    boolean forced = false;
                    for (int i = 0; i < indices.size() && !forced; i++) {
                        int prevIdx = (i - 1 + indices.size()) % indices.size();
                        int nextIdx = (i + 1) % indices.size();
                        double[] a = RingMathUtil.project2D(vertices.get(indices.get(prevIdx)), projAxis);
                        double[] b = RingMathUtil.project2D(vertices.get(indices.get(i)), projAxis);
                        double[] c = RingMathUtil.project2D(vertices.get(indices.get(nextIdx)), projAxis);
                        double cross = cross2D(a, b, c);
                        if (Math.abs(cross) > RingMathUtil.TOLERANCE) {
                            // Containment check: skip if any other vertex is
                            // inside — same predicate as the normal ear path,
                            // but here also accepting concave (CW) triples.
                            boolean valid = !triangleContainsOtherVertex(
                                    indices, vertices, projAxis, prevIdx, i, nextIdx, a, b, c);
                            if (valid) {
                                if (cross > 0) {
                                    triangles.add(new int[]{indices.get(prevIdx), indices.get(i), indices.get(nextIdx)});
                                } else {
                                    triangles.add(new int[]{indices.get(nextIdx), indices.get(i), indices.get(prevIdx)});
                                }
                                indices.remove(i);
                                forced = true;
                            }
                        }
                    }
                    if (!forced) {
                        // All remaining vertices are collinear or all triples
                        // contain other vertices — remove most-collinear vertex
                        // to simplify the polygon and retry
                        int removeIdx = 0;
                        double minAbsCross = Double.MAX_VALUE;
                        for (int i = 0; i < indices.size(); i++) {
                            int prevIdx = (i - 1 + indices.size()) % indices.size();
                            int nextIdx = (i + 1) % indices.size();
                            double[] a = RingMathUtil.project2D(vertices.get(indices.get(prevIdx)), projAxis);
                            double[] b = RingMathUtil.project2D(vertices.get(indices.get(i)), projAxis);
                            double[] c = RingMathUtil.project2D(vertices.get(indices.get(nextIdx)), projAxis);
                            double absCross = Math.abs(cross2D(a, b, c));
                            if (absCross < minAbsCross) {
                                minAbsCross = absCross;
                                removeIdx = i;
                            }
                        }
                        indices.remove(removeIdx);
                    }
                }
                attempts++;
            }
        }

        return triangles;
    }

    /**
     * Whether any polygon vertex other than the candidate triple
     * ({@code prevIdx}, {@code i}, {@code nextIdx}, indices into {@code indices})
     * lies inside triangle (a, b, c) in the projection plane. Vertices
     * coincident with a corner are keyhole-bridge duplicates and ignored —
     * counting them as "inside" would block legitimate ears and leave a bridged
     * hole filled. Shared by the normal ear test and the forced-triple fallback
     * in {@link #clip}; {@code a}/{@code b}/{@code c} are the already-projected
     * corners the caller derived from the triple.
     */
    private static boolean triangleContainsOtherVertex(
            List<Integer> indices, List<double[]> vertices, int projAxis,
            int prevIdx, int i, int nextIdx,
            double[] a, double[] b, double[] c) {
        for (int j = 0; j < indices.size(); j++) {
            if (j == prevIdx || j == i || j == nextIdx) {
                continue;
            }
            double[] p = RingMathUtil.project2D(vertices.get(indices.get(j)), projAxis);
            if (RingMathUtil.coincident2D(p, a) || RingMathUtil.coincident2D(p, b)
                    || RingMathUtil.coincident2D(p, c)) {
                continue;
            }
            if (pointInTriangle(p, a, b, c)) {
                return true;
            }
        }
        return false;
    }

    private static double cross2D(double[] a, double[] b, double[] c) {
        return (b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0]);
    }

    private static boolean pointInTriangle(double[] p, double[] a, double[] b, double[] c) {
        double d1 = cross2D(a, b, p);
        double d2 = cross2D(b, c, p);
        double d3 = cross2D(c, a, p);

        boolean hasNeg = (d1 < -RingMathUtil.TOLERANCE) || (d2 < -RingMathUtil.TOLERANCE)
                || (d3 < -RingMathUtil.TOLERANCE);
        boolean hasPos = (d1 > RingMathUtil.TOLERANCE) || (d2 > RingMathUtil.TOLERANCE)
                || (d3 > RingMathUtil.TOLERANCE);

        return !(hasNeg && hasPos);
    }
}
