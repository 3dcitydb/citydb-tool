/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.geometry;

import org.citydb.model.appearance.TextureCoordinate;
import org.citydb.model.geometry.Coordinate;
import org.citydb.model.geometry.Geometry;
import org.citydb.model.geometry.LinearRing;
import org.citydb.model.geometry.Polygon;
import org.citydb.model.walker.ModelWalker;
import org.citydb.vis.util.GeoTransform;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Triangulates polygon surfaces using an ear-clipping algorithm.
 * Handles EPSG:4326 coordinates (lon/lat in degrees, height in meters)
 * by converting to local meters for triangulation math.
 * <p>
 * Instances carry per-feature polygon-deduplication state: CityGML
 * {@code xlink:href} references to shared surfaces (e.g., a wall polygon
 * reachable from both a LoD2 Solid and its BoundarySurface child) produce
 * multiple {@link Polygon} objects that share the same {@code gml:id}. Without
 * dedup the same polygon is triangulated multiple times and emitted as
 * coincident faces, causing Z-fighting / flicker in the viewer. Reuse a single
 * triangulator across all {@link #triangulate} calls for one feature so the
 * dedup spans every geometry property of that feature.
 */
public class PolygonTriangulator {
    private static final double TOLERANCE = 1e-7;

    private record RingData(List<double[]> positions, List<float[]> uvs) {}

    // Per-feature dedup state. State persists across triangulate() calls on
    // the same instance so dedup covers a feature's full geometry (each
    // feature owns a fresh PolygonTriangulator; see GeometryMeshBuilder).
    // The identity set catches the shared-instance case (same Java object
    // reachable via two parents); the id/reversed key catches the xlink-copy
    // case, where the CityGML reader rebuilds a fresh Polygon instance per
    // reference and all instances carry the source gml:id.
    private final Set<Polygon> seenInstances =
            Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<String> seenIds = new HashSet<>();

    public TriangleMesh triangulate(Geometry<?> geometry, long featureId,
                                    Map<LinearRing, List<TextureCoordinate>> texCoordMap,
                                    Map<LinearRing, Integer> ringTextureMap) {
        TriangleMesh mesh = new TriangleMesh();
        List<Polygon> polygons = collectPolygons(geometry);

        for (Polygon polygon : polygons) {
            triangulatePolygon(polygon, featureId, mesh, texCoordMap, ringTextureMap);
        }

        return mesh;
    }

    private List<Polygon> collectPolygons(Geometry<?> geometry) {
        List<Polygon> polygons = new ArrayList<>();
        geometry.accept(new ModelWalker() {
            @Override
            public void visit(Polygon polygon) {
                if (!seenInstances.add(polygon)) {
                    return;
                }
                // Dedup key is (gml:id, reversed): xlink-shared polygons carry
                // the same gml:id and same orientation — drop them. A polygon
                // wrapped in gml:OrientableSurface with opposite sign has the
                // same gml:id but inverted `reversed`, representing a distinct
                // face (e.g., the back side of a shared wall); keep it.
                String id = polygon.getObjectId().orElse(null);
                if (id != null) {
                    String key = polygon.isReversed() ? id + "!r" : id;
                    if (!seenIds.add(key)) {
                        return;
                    }
                }
                polygons.add(polygon);
            }
        });
        return polygons;
    }

    private static void triangulatePolygon(Polygon polygon, long featureId, TriangleMesh mesh,
                                    Map<LinearRing, List<TextureCoordinate>> texCoordMap,
                                    Map<LinearRing, Integer> ringTextureMap) {
        LinearRing exteriorRing = polygon.getExteriorRing();
        List<Coordinate> outerPoints = exteriorRing.getPoints();

        if (outerPoints.size() < 4) {
            return;
        }

        // Look up UV coordinates and texture ID for the exterior ring
        List<TextureCoordinate> outerTexCoords = texCoordMap != null
                ? texCoordMap.get(exteriorRing) : null;
        boolean hasUV = outerTexCoords != null && outerTexCoords.size() >= outerPoints.size();
        int polyTextureId = (ringTextureMap != null)
                ? ringTextureMap.getOrDefault(exteriorRing, -1) : -1;

        // Compute centroid latitude for degree-to-meter conversion
        double centroidLat = 0;
        for (Coordinate c : outerPoints) {
            centroidLat += c.getY();
        }
        centroidLat /= outerPoints.size();
        double scaleX = GeoTransform.metersPerDegreeLon(centroidLat);
        double scaleY = GeoTransform.WGS84_METERS_PER_DEGREE_LAT;

        // Build original coordinate ring and parallel UV ring (degrees/meters)
        List<double[]> ring;
        List<float[]> uvRing;
        if (polygon.hasInteriorRings()) {
            RingData bridged = bridgeHolesWithUV(outerPoints,
                    hasUV ? toUVArray(outerTexCoords) : null,
                    polygon.getInteriorRings(), texCoordMap, scaleX, scaleY);
            ring = bridged.positions;
            uvRing = bridged.uvs;
        } else {
            ring = toDoubleArray(outerPoints);
            if (ring.size() > 1) {
                ring.remove(ring.size() - 1);
            }
            if (hasUV) {
                uvRing = toUVArray(outerTexCoords);
                if (uvRing.size() > 1) {
                    uvRing.remove(uvRing.size() - 1);
                }
            } else {
                uvRing = null;
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
            Collections.reverse(ring);
            Collections.reverse(scaledRing);
            if (uvRing != null) {
                Collections.reverse(uvRing);
            }
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
        for (int i = 0; i < ring.size(); i++) {
            double[] pt = ring.get(i);
            if (uvRing != null) {
                float[] uv = uvRing.get(i);
                mesh.addVertex(pt[0], pt[1], pt[2], normal[0], normal[1], normal[2], uv[0], uv[1]);
            } else {
                mesh.addVertex(pt[0], pt[1], pt[2], normal[0], normal[1], normal[2]);
            }
        }

        // Add triangles — if original polygon was CW, swap winding to restore face direction
        for (int[] tri : triangleIndices) {
            if (reverseWinding) {
                mesh.addTriangle(
                        baseVertex + tri[0],
                        baseVertex + tri[2],
                        baseVertex + tri[1],
                        featureId, polyTextureId);
            } else {
                mesh.addTriangle(
                        baseVertex + tri[0],
                        baseVertex + tri[1],
                        baseVertex + tri[2],
                        featureId, polyTextureId);
            }
        }
    }

    /**
     * Compute the polygon normal using the Newell method.
     * Expects coordinates in consistent units (meters).
     */
    private static float[] computePolygonNormal(List<double[]> points) {
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

    private static boolean isZeroVector(float[] v) {
        return Math.abs(v[0]) < TOLERANCE && Math.abs(v[1]) < TOLERANCE && Math.abs(v[2]) < TOLERANCE;
    }

    private static int getDominantAxis(float[] normal) {
        float absX = Math.abs(normal[0]);
        float absY = Math.abs(normal[1]);
        float absZ = Math.abs(normal[2]);

        if (absX >= absY && absX >= absZ) return 0;
        if (absY >= absX && absY >= absZ) return 1;
        return 2;
    }

    private static double[] project2D(double[] point, int projAxis) {
        return switch (projAxis) {
            case 0 -> new double[]{point[1], point[2]};
            case 1 -> new double[]{point[0], point[2]};
            default -> new double[]{point[0], point[1]};
        };
    }

    private static RingData bridgeHolesWithUV(List<Coordinate> outerPoints, List<float[]> outerUVs,
                                       List<LinearRing> holes,
                                       Map<LinearRing, List<TextureCoordinate>> texCoordMap,
                                       double scaleX, double scaleY) {
        List<double[]> result = toDoubleArray(outerPoints);
        if (result.size() > 1) {
            result.remove(result.size() - 1);
        }
        List<float[]> uvResult = outerUVs != null ? new ArrayList<>(outerUVs) : null;
        if (uvResult != null && uvResult.size() > 1) {
            uvResult.remove(uvResult.size() - 1);
        }

        for (LinearRing hole : holes) {
            List<double[]> holePoints = toDoubleArray(hole.getPoints());
            if (holePoints.size() > 1) {
                holePoints.remove(holePoints.size() - 1);
            }
            if (holePoints.size() < 3) {
                continue;
            }

            // Look up UV for this hole ring.
            // Note: holePoints has already had its closing-coordinate duplicate
            // removed above, so the `>` here is equivalent to the outer ring's
            // `>= outerPoints.size()` check (which runs before its own removal).
            // Both require UVs to have N+1 entries (i.e. UV list includes the
            // closing-coordinate duplication, matching the ring's raw size).
            List<float[]> holeUVs = null;
            if (uvResult != null && texCoordMap != null) {
                List<TextureCoordinate> holeTexCoords = texCoordMap.get(hole);
                if (holeTexCoords != null && holeTexCoords.size() > holePoints.size()) {
                    holeUVs = toUVArray(holeTexCoords);
                    if (holeUVs.size() > 1) {
                        holeUVs.remove(holeUVs.size() - 1);
                    }
                }
            }

            // Find the rightmost point of the hole
            int holeIdx = 0;
            double maxU = Double.NEGATIVE_INFINITY;
            for (int i = 0; i < holePoints.size(); i++) {
                double u = holePoints.get(i)[0] * scaleX;
                if (u > maxU) {
                    maxU = u;
                    holeIdx = i;
                }
            }

            int outerIdx = findClosestVisible(result, holePoints.get(holeIdx), scaleX, scaleY);

            // Bridge: insert hole into outer ring at the connection point
            List<double[]> merged = new ArrayList<>(result.size() + holePoints.size() + 2);
            List<float[]> mergedUV = uvResult != null
                    ? new ArrayList<>(result.size() + holePoints.size() + 2) : null;

            for (int i = 0; i <= outerIdx; i++) {
                merged.add(result.get(i));
                if (mergedUV != null) mergedUV.add(uvResult.get(i));
            }
            for (int i = 0; i < holePoints.size(); i++) {
                int idx = (holeIdx + i) % holePoints.size();
                merged.add(holePoints.get(idx));
                if (mergedUV != null) {
                    mergedUV.add(holeUVs != null ? holeUVs.get(idx) : new float[]{0f, 0f});
                }
            }
            // Close the hole bridge
            merged.add(holePoints.get(holeIdx));
            if (mergedUV != null) {
                mergedUV.add(holeUVs != null ? holeUVs.get(holeIdx) : new float[]{0f, 0f});
            }
            merged.add(result.get(outerIdx));
            if (mergedUV != null) mergedUV.add(uvResult.get(outerIdx));

            for (int i = outerIdx + 1; i < result.size(); i++) {
                merged.add(result.get(i));
                if (mergedUV != null) mergedUV.add(uvResult.get(i));
            }

            result = merged;
            uvResult = mergedUV;
        }

        return new RingData(result, uvResult);
    }

    private static List<float[]> toUVArray(List<TextureCoordinate> texCoords) {
        List<float[]> result = new ArrayList<>(texCoords.size());
        for (TextureCoordinate tc : texCoords) {
            // CityGML: T=0 at bottom (OGC convention).
            // glTF (I3S / 3D Tiles): V=0 at top. Flip V axis.
            result.add(new float[]{tc.getS(), 1.0f - tc.getT()});
        }
        return result;
    }

    /**
     * Find the closest point on the outer ring that is visible from the hole point,
     * i.e. the bridge segment does not cross any existing edge of the ring.
     */
    private static int findClosestVisible(List<double[]> ring, double[] holePoint,
                                   double scaleX, double scaleY) {
        double hpx = holePoint[0] * scaleX;
        double hpy = holePoint[1] * scaleY;
        double minDist = Double.MAX_VALUE;
        int closest = 0;

        for (int i = 0; i < ring.size(); i++) {
            double rx = ring.get(i)[0] * scaleX;
            double ry = ring.get(i)[1] * scaleY;
            double dist = (rx - hpx) * (rx - hpx) + (ry - hpy) * (ry - hpy);
            if (dist < minDist
                    && !bridgeCrossesEdge(ring, hpx, hpy, rx, ry, i, scaleX, scaleY)) {
                minDist = dist;
                closest = i;
            }
        }

        return closest;
    }

    /**
     * Check if the bridge segment (px,py)→(qx,qy) crosses any edge of the ring,
     * excluding edges that share the vertex at {@code skipVertex}.
     */
    private static boolean bridgeCrossesEdge(List<double[]> ring,
                                             double px, double py, double qx, double qy,
                                             int skipVertex, double scaleX, double scaleY) {
        int n = ring.size();
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            if (i == skipVertex || j == skipVertex) continue;

            double ax = ring.get(i)[0] * scaleX;
            double ay = ring.get(i)[1] * scaleY;
            double bx = ring.get(j)[0] * scaleX;
            double by = ring.get(j)[1] * scaleY;

            if (segmentsIntersectStrict(px, py, qx, qy, ax, ay, bx, by)) {
                return true;
            }
        }
        return false;
    }

    /** Strict segment intersection test (excludes endpoint touching). */
    private static boolean segmentsIntersectStrict(double p1x, double p1y, double p2x, double p2y,
                                                   double p3x, double p3y, double p4x, double p4y) {
        double d1 = cross2D(p3x, p3y, p4x, p4y, p1x, p1y);
        double d2 = cross2D(p3x, p3y, p4x, p4y, p2x, p2y);
        double d3 = cross2D(p1x, p1y, p2x, p2y, p3x, p3y);
        double d4 = cross2D(p1x, p1y, p2x, p2y, p4x, p4y);

        return ((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0))
                && ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0));
    }

    /** Cross product of vectors (o→a) × (o→b). */
    private static double cross2D(double ox, double oy, double ax, double ay,
                                  double bx, double by) {
        return (ax - ox) * (by - oy) - (ay - oy) * (bx - ox);
    }

    /**
     * Ear clipping triangulation algorithm.
     * Operates on scaled coordinates (meters) for correct geometric tests.
     */
    private static List<int[]> earClip(List<double[]> vertices, int projAxis) {
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
                    // Fallback: find a non-degenerate triple that does NOT contain
                    // any other polygon vertex — same containment check as the
                    // normal ear path, but also accepting concave (CW) triples.
                    boolean forced = false;
                    for (int i = 0; i < indices.size() && !forced; i++) {
                        int prevIdx = (i - 1 + indices.size()) % indices.size();
                        int nextIdx = (i + 1) % indices.size();
                        double[] a = project2D(vertices.get(indices.get(prevIdx)), projAxis);
                        double[] b = project2D(vertices.get(indices.get(i)), projAxis);
                        double[] c = project2D(vertices.get(indices.get(nextIdx)), projAxis);
                        double cross = cross2D(a, b, c);
                        if (Math.abs(cross) > TOLERANCE) {
                            // Containment check: skip if any other vertex is inside
                            boolean valid = true;
                            for (int j = 0; j < indices.size(); j++) {
                                if (j == prevIdx || j == i || j == nextIdx) continue;
                                double[] p = project2D(vertices.get(indices.get(j)), projAxis);
                                if (pointInTriangle(p, a, b, c)) {
                                    valid = false;
                                    break;
                                }
                            }
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
                            double[] a = project2D(vertices.get(indices.get(prevIdx)), projAxis);
                            double[] b = project2D(vertices.get(indices.get(i)), projAxis);
                            double[] c = project2D(vertices.get(indices.get(nextIdx)), projAxis);
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

    private static double cross2D(double[] a, double[] b, double[] c) {
        return (b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0]);
    }

    private static boolean pointInTriangle(double[] p, double[] a, double[] b, double[] c) {
        double d1 = cross2D(a, b, p);
        double d2 = cross2D(b, c, p);
        double d3 = cross2D(c, a, p);

        boolean hasNeg = (d1 < -TOLERANCE) || (d2 < -TOLERANCE) || (d3 < -TOLERANCE);
        boolean hasPos = (d1 > TOLERANCE) || (d2 > TOLERANCE) || (d3 > TOLERANCE);

        return !(hasNeg && hasPos);
    }

    private static boolean isCCW(List<double[]> ring, int projAxis) {
        double area = 0;
        for (int i = 0; i < ring.size(); i++) {
            double[] a = project2D(ring.get(i), projAxis);
            double[] b = project2D(ring.get((i + 1) % ring.size()), projAxis);
            area += (b[0] - a[0]) * (b[1] + a[1]);
        }
        return area < 0;
    }

    private static List<double[]> toDoubleArray(List<Coordinate> coordinates) {
        List<double[]> result = new ArrayList<>(coordinates.size());
        for (Coordinate c : coordinates) {
            result.add(new double[]{c.getX(), c.getY(), c.getZ()});
        }
        return result;
    }
}
