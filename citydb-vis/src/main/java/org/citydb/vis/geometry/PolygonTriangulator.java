/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.vis.geometry;

import org.citydb.model.geometry.*;
import org.citydb.model.walker.ModelWalker;

import java.util.ArrayList;
import java.util.List;

/**
 * Triangulates polygon surfaces using an ear-clipping algorithm.
 * Handles EPSG:4326 coordinates (lon/lat in degrees, height in meters)
 * by converting to local meters for triangulation math.
 */
public class PolygonTriangulator {
    private static final double TOLERANCE = 1e-7;
    private static final double METERS_PER_DEGREE_LAT = 111_320.0;


    public TriangleMesh triangulate(Geometry<?> geometry, long featureId) {
        TriangleMesh mesh = new TriangleMesh();
        List<Polygon> polygons = collectPolygons(geometry);

        for (Polygon polygon : polygons) {
            triangulatePolygon(polygon, featureId, mesh);
        }

        return mesh;
    }

    private List<Polygon> collectPolygons(Geometry<?> geometry) {
        List<Polygon> polygons = new ArrayList<>();
        geometry.accept(new ModelWalker() {
            @Override
            public void visit(Polygon polygon) {
                polygons.add(polygon);
            }
        });
        return polygons;
    }

    private void triangulatePolygon(Polygon polygon, long featureId, TriangleMesh mesh) {
        LinearRing exteriorRing = polygon.getExteriorRing();
        List<Coordinate> outerPoints = exteriorRing.getPoints();

        if (outerPoints.size() < 4) {
            return;
        }

        // Compute centroid latitude for degree-to-meter conversion
        double centroidLat = 0;
        for (Coordinate c : outerPoints) {
            centroidLat += c.getY();
        }
        centroidLat /= outerPoints.size();
        double scaleX = METERS_PER_DEGREE_LAT * Math.cos(Math.toRadians(centroidLat));
        double scaleY = METERS_PER_DEGREE_LAT;

        // Build original coordinate ring (degrees/meters)
        List<double[]> ring;
        if (polygon.hasInteriorRings()) {
            ring = bridgeHoles(outerPoints, polygon.getInteriorRings(), scaleX, scaleY);
        } else {
            ring = toDoubleArray(outerPoints);
            if (ring.size() > 1) {
                ring.remove(ring.size() - 1);
            }
        }

        if (ring.size() < 3) {
            return;
        }

        // Build scaled ring (all meters) for triangulation math
        List<double[]> scaledRing = new ArrayList<>(ring.size());
        for (double[] pt : ring) {
            scaledRing.add(new double[]{pt[0] * scaleX, pt[1] * scaleY, pt[2]});
        }

        // Compute polygon normal using scaled coordinates (all in meters)
        float[] normal = computePolygonNormal(scaledRing);
        if (isZeroVector(normal)) {
            return;
        }

        int projAxis = getDominantAxis(normal);

        // Ensure CCW winding for ear-clipping algorithm, but preserve original orientation.
        // Track whether the ring was reversed so we can restore the correct face direction.
        boolean reverseWinding = !isCCW(scaledRing, projAxis);
        if (reverseWinding) {
            reverseList(ring);
            reverseList(scaledRing);
        }

        // Apply polygon reversed flag (e.g., shared polygon in a solid)
        if (polygon.isReversed()) {
            normal[0] = -normal[0];
            normal[1] = -normal[1];
            normal[2] = -normal[2];
            reverseWinding = !reverseWinding;
        }

        // Ear clipping using scaled coordinates (requires CCW winding)
        List<int[]> triangleIndices = earClip(scaledRing, projAxis);

        // Add vertices using ORIGINAL coordinates (degrees/meters) for output
        int baseVertex = mesh.getVertexCount();
        for (double[] pt : ring) {
            mesh.addVertex(pt[0], pt[1], pt[2], normal[0], normal[1], normal[2]);
        }

        // Add triangles — if original polygon was CW, swap winding to restore face direction
        for (int[] tri : triangleIndices) {
            if (reverseWinding) {
                mesh.addTriangle(
                        baseVertex + tri[0],
                        baseVertex + tri[2],
                        baseVertex + tri[1],
                        featureId);
            } else {
                mesh.addTriangle(
                        baseVertex + tri[0],
                        baseVertex + tri[1],
                        baseVertex + tri[2],
                        featureId);
            }
        }
    }

    /**
     * Compute the polygon normal using the Newell method.
     * Expects coordinates in consistent units (meters).
     */
    private float[] computePolygonNormal(List<double[]> points) {
        double nx = 0, ny = 0, nz = 0;
        int n = points.size();

        for (int i = 0; i < n; i++) {
            double[] current = points.get(i);
            double[] next = points.get((i + 1) % n);

            nx += (current[1] - next[1]) * (current[2] + next[2]);
            ny += (current[2] - next[2]) * (current[0] + next[0]);
            nz += (current[0] - next[0]) * (current[1] + next[1]);
        }

        double length = Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (length < TOLERANCE) {
            return new float[]{0, 0, 0};
        }

        return new float[]{(float) (nx / length), (float) (ny / length), (float) (nz / length)};
    }

    private boolean isZeroVector(float[] v) {
        return Math.abs(v[0]) < TOLERANCE && Math.abs(v[1]) < TOLERANCE && Math.abs(v[2]) < TOLERANCE;
    }

    private int getDominantAxis(float[] normal) {
        float absX = Math.abs(normal[0]);
        float absY = Math.abs(normal[1]);
        float absZ = Math.abs(normal[2]);

        if (absX >= absY && absX >= absZ) return 0;
        if (absY >= absX && absY >= absZ) return 1;
        return 2;
    }

    private double[] project2D(double[] point, int projAxis) {
        return switch (projAxis) {
            case 0 -> new double[]{point[1], point[2]};
            case 1 -> new double[]{point[0], point[2]};
            default -> new double[]{point[0], point[1]};
        };
    }

    private List<double[]> bridgeHoles(List<Coordinate> outerPoints, List<LinearRing> holes,
                                       double scaleX, double scaleY) {
        List<double[]> result = toDoubleArray(outerPoints);
        if (result.size() > 1) {
            result.remove(result.size() - 1);
        }

        for (LinearRing hole : holes) {
            List<double[]> holePoints = toDoubleArray(hole.getPoints());
            if (holePoints.size() > 1) {
                holePoints.remove(holePoints.size() - 1);
            }

            if (holePoints.size() < 3) {
                continue;
            }

            // Find the rightmost point of the hole (max X in scaled/projected space)
            int holeIdx = 0;
            double maxU = Double.NEGATIVE_INFINITY;
            for (int i = 0; i < holePoints.size(); i++) {
                double u = holePoints.get(i)[0] * scaleX;
                if (u > maxU) {
                    maxU = u;
                    holeIdx = i;
                }
            }

            // Find the closest visible point on the outer ring
            int outerIdx = findClosestVisible(result, holePoints.get(holeIdx), scaleX, scaleY);

            // Bridge: insert hole into outer ring at the connection point
            List<double[]> merged = new ArrayList<>(result.size() + holePoints.size() + 2);
            for (int i = 0; i <= outerIdx; i++) {
                merged.add(result.get(i));
            }

            for (int i = 0; i < holePoints.size(); i++) {
                merged.add(holePoints.get((holeIdx + i) % holePoints.size()));
            }
            merged.add(holePoints.get(holeIdx));
            merged.add(result.get(outerIdx));

            for (int i = outerIdx + 1; i < result.size(); i++) {
                merged.add(result.get(i));
            }

            result = merged;
        }

        return result;
    }

    private int findClosestVisible(List<double[]> ring, double[] holePoint,
                                   double scaleX, double scaleY) {
        double hpx = holePoint[0] * scaleX;
        double hpy = holePoint[1] * scaleY;
        double minDist = Double.MAX_VALUE;
        int closest = 0;

        for (int i = 0; i < ring.size(); i++) {
            double rx = ring.get(i)[0] * scaleX;
            double ry = ring.get(i)[1] * scaleY;
            double dist = (rx - hpx) * (rx - hpx) + (ry - hpy) * (ry - hpy);
            if (dist < minDist) {
                minDist = dist;
                closest = i;
            }
        }

        return closest;
    }

    /**
     * Ear clipping triangulation algorithm.
     * Operates on scaled coordinates (meters) for correct geometric tests.
     */
    private List<int[]> earClip(List<double[]> vertices, int projAxis) {
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

                double[] a = project2D(vertices.get(prev), projAxis);
                double[] b = project2D(vertices.get(curr), projAxis);
                double[] c = project2D(vertices.get(next), projAxis);

                if (cross2D(a, b, c) <= TOLERANCE) {
                    attempts++;
                    continue;
                }

                boolean isEar = true;
                for (int j = 0; j < indices.size(); j++) {
                    if (j == prevIdx || j == i || j == nextIdx) continue;
                    double[] p = project2D(vertices.get(indices.get(j)), projAxis);
                    if (pointInTriangle(p, a, b, c)) {
                        isEar = false;
                        break;
                    }
                }

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
                    // Try to find a non-degenerate triangle among remaining vertices,
                    // preserving CCW winding order required by the algorithm.
                    boolean forced = false;
                    for (int i = 0; i < indices.size() && !forced; i++) {
                        int prevIdx = (i - 1 + indices.size()) % indices.size();
                        int nextIdx = (i + 1) % indices.size();
                        double[] a = project2D(vertices.get(indices.get(prevIdx)), projAxis);
                        double[] b = project2D(vertices.get(indices.get(i)), projAxis);
                        double[] c = project2D(vertices.get(indices.get(nextIdx)), projAxis);
                        double cross = cross2D(a, b, c);
                        if (Math.abs(cross) > TOLERANCE) {
                            if (cross > 0) {
                                triangles.add(new int[]{indices.get(prevIdx), indices.get(i), indices.get(nextIdx)});
                            } else {
                                // Negative cross = CW triple — swap to keep CCW winding
                                triangles.add(new int[]{indices.get(nextIdx), indices.get(i), indices.get(prevIdx)});
                            }
                            indices.remove(i);
                            forced = true;
                        }
                    }
                    if (!forced) {
                        // All remaining vertices are collinear — skip without creating
                        // a degenerate triangle
                        indices.remove(0);
                    }
                }
                attempts++;
            }
        }

        return triangles;
    }

    private double cross2D(double[] a, double[] b, double[] c) {
        return (b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0]);
    }

    private boolean pointInTriangle(double[] p, double[] a, double[] b, double[] c) {
        double d1 = cross2D(a, b, p);
        double d2 = cross2D(b, c, p);
        double d3 = cross2D(c, a, p);

        boolean hasNeg = (d1 < -TOLERANCE) || (d2 < -TOLERANCE) || (d3 < -TOLERANCE);
        boolean hasPos = (d1 > TOLERANCE) || (d2 > TOLERANCE) || (d3 > TOLERANCE);

        return !(hasNeg && hasPos);
    }

    private boolean isCCW(List<double[]> ring, int projAxis) {
        double area = 0;
        for (int i = 0; i < ring.size(); i++) {
            double[] a = project2D(ring.get(i), projAxis);
            double[] b = project2D(ring.get((i + 1) % ring.size()), projAxis);
            area += (b[0] - a[0]) * (b[1] + a[1]);
        }
        return area < 0;
    }

    private List<double[]> toDoubleArray(List<Coordinate> coordinates) {
        List<double[]> result = new ArrayList<>(coordinates.size());
        for (Coordinate c : coordinates) {
            result.add(new double[]{c.getX(), c.getY(), c.getZ()});
        }
        return result;
    }

    private void reverseList(List<double[]> list) {
        int left = 0, right = list.size() - 1;
        while (left < right) {
            double[] temp = list.get(left);
            list.set(left, list.get(right));
            list.set(right, temp);
            left++;
            right--;
        }
    }
}
