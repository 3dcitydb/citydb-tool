/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.geometry;

import org.citydb.model.appearance.TextureCoordinate;
import org.citydb.model.common.Name;
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

    private record RingData(List<double[]> positions, List<double[]> scaledPositions,
                            List<float[]> uvs) {}

    // Per-feature dedup state. State persists across triangulate() calls on
    // the same instance so dedup covers a feature's full geometry (each
    // feature owns a fresh PolygonTriangulator; see GeometryMeshBuilder).
    // The identity set catches the shared-instance case (same Java object
    // reachable via two parents); the id/reversed key catches the xlink-copy
    // case, where the CityGML reader rebuilds a fresh Polygon instance per
    // reference and all instances carry the source gml:id.
    //
    // Dedup is "first-wins" — the first triangulate() call to encounter a
    // given polygon records its surface type, all later calls that touch the
    // same polygon are silently dropped. GeometryMeshBuilder relies on this
    // by sorting properties most-specific-owner-first before triangulating,
    // so a polygon shared between a Building's lod3Solid and one of its
    // boundedBy surfaces is recorded with the BoundarySurface's type rather
    // than Building's. Don't break that ordering contract upstream.
    private final Set<Polygon> seenInstances =
            Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<String> seenIds = new HashSet<>();

    public TriangleMesh triangulate(Geometry<?> geometry, long featureId, Name surfaceType,
                                    Map<LinearRing, List<TextureCoordinate>> texCoordMap,
                                    Map<LinearRing, Integer> ringTextureMap,
                                    Map<LinearRing, float[]> ringColorMap) {
        TriangleMesh mesh = new TriangleMesh();
        List<Polygon> polygons = collectPolygons(geometry);

        for (Polygon polygon : polygons) {
            triangulatePolygon(polygon, featureId, surfaceType, mesh, texCoordMap, ringTextureMap,
                    ringColorMap);
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

    private static void triangulatePolygon(Polygon polygon, long featureId, Name surfaceType,
                                    TriangleMesh mesh,
                                    Map<LinearRing, List<TextureCoordinate>> texCoordMap,
                                    Map<LinearRing, Integer> ringTextureMap,
                                    Map<LinearRing, float[]> ringColorMap) {
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
        // Per-polygon RGBA from X3DMaterial; null when the ring has no
        // material assignment. Texture wins over material — the extractor
        // already drops material entries for rings that have a texture id.
        float[] polyColor = ringColorMap != null ? ringColorMap.get(exteriorRing) : null;

        // Compute centroid latitude for degree-to-meter conversion
        double centroidLat = 0;
        for (Coordinate c : outerPoints) {
            centroidLat += c.getY();
        }
        centroidLat /= outerPoints.size();
        double scaleX = GeoTransform.metersPerDegreeLon(centroidLat);
        double scaleY = GeoTransform.WGS84_METERS_PER_DEGREE_LAT;

        // Build the outer ring (without the closing duplicate vertex) in
        // both original (degrees/meters) and scaled (all-meters) form.
        List<double[]> outerRing = toDoubleArray(outerPoints);
        if (outerRing.size() > 1) {
            outerRing.remove(outerRing.size() - 1);
        }
        if (outerRing.size() < 3) {
            return;
        }
        List<double[]> outerScaled = scaleRing(outerRing, scaleX, scaleY);

        // Compute the polygon plane (normal + dominant axis) from the OUTER
        // ring alone — interior rings are coplanar and share this projection.
        // Doing this BEFORE bridging is required: keyhole bridging done in a
        // fixed XY plane collapses for vertical walls (whose plane is XZ or
        // YZ), producing a self-intersecting merged ring that ear-clipping
        // then "fills" — visible as walls with missing window/door holes.
        float[] normal = computePolygonNormal(outerScaled);
        if (isZeroVector(normal)) {
            return;
        }
        int projAxis = getDominantAxis(normal);

        List<float[]> outerUVRing = null;
        if (hasUV) {
            outerUVRing = toUVArray(outerTexCoords);
            if (outerUVRing.size() > 1) {
                outerUVRing.remove(outerUVRing.size() - 1);
            }
        }

        // Bridge interior rings (holes) into the outer ring using the
        // dominant projection axis so bridging works on every plane,
        // including vertical walls.
        List<double[]> ring;
        List<double[]> scaledRing;
        List<float[]> uvRing;
        if (polygon.hasInteriorRings()) {
            RingData bridged = bridgeHolesWithUV(outerRing, outerScaled, outerUVRing,
                    polygon.getInteriorRings(), texCoordMap, scaleX, scaleY, projAxis);
            ring = bridged.positions;
            scaledRing = bridged.scaledPositions;
            uvRing = bridged.uvs;
        } else {
            ring = outerRing;
            scaledRing = outerScaled;
            uvRing = outerUVRing;
        }

        if (ring.size() < 3) {
            return;
        }

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

        // Add vertices using ORIGINAL coordinates (degrees/meters) for output.
        // A polygon never carries both UV and material color (extractor drops
        // material entries for textured rings), so the four call paths are
        // mutually exclusive.
        int baseVertex = mesh.getVertexCount();
        for (int i = 0; i < ring.size(); i++) {
            double[] pt = ring.get(i);
            if (uvRing != null) {
                float[] uv = uvRing.get(i);
                mesh.addVertex(pt[0], pt[1], pt[2], normal[0], normal[1], normal[2], uv[0], uv[1]);
            } else if (polyColor != null) {
                mesh.addVertex(pt[0], pt[1], pt[2], normal[0], normal[1], normal[2],
                        polyColor[0], polyColor[1], polyColor[2], polyColor[3]);
            } else {
                mesh.addVertex(pt[0], pt[1], pt[2], normal[0], normal[1], normal[2]);
            }
        }

        // Add triangles — if original polygon was CW, swap winding to restore face direction.
        // The colored flag flows from the X3DMaterial extractor; see
        // TriangleMesh.isTriangleColored for how the GLB writer consumes it.
        boolean polyColored = polyColor != null;
        for (int[] tri : triangleIndices) {
            if (reverseWinding) {
                mesh.addTriangle(
                        baseVertex + tri[0],
                        baseVertex + tri[2],
                        baseVertex + tri[1],
                        featureId, polyTextureId, polyColored, surfaceType);
            } else {
                mesh.addTriangle(
                        baseVertex + tri[0],
                        baseVertex + tri[1],
                        baseVertex + tri[2],
                        featureId, polyTextureId, polyColored, surfaceType);
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

    /**
     * Maximum projected u-coordinate of a ring's vertices in the polygon's
     * dominant projection plane. Used to order interior rings before
     * keyhole-bridging so the easternmost hole goes first.
     */
    private static double maxProjectedU(LinearRing ring, double scaleX, double scaleY, int projAxis) {
        double max = Double.NEGATIVE_INFINITY;
        for (Coordinate c : ring.getPoints()) {
            double[] scaled = {c.getX() * scaleX, c.getY() * scaleY, c.getZ()};
            double u = project2D(scaled, projAxis)[0];
            if (u > max) max = u;
        }
        return max;
    }

    private static RingData bridgeHolesWithUV(
            List<double[]> outerRing, List<double[]> outerScaled, List<float[]> outerUVs,
            List<LinearRing> holes, Map<LinearRing, List<TextureCoordinate>> texCoordMap,
            double scaleX, double scaleY, int projAxis) {
        List<double[]> result = new ArrayList<>(outerRing);
        List<double[]> resultScaled = new ArrayList<>(outerScaled);
        List<float[]> uvResult = outerUVs != null ? new ArrayList<>(outerUVs) : null;

        // Outer winding sign in the projection plane — used to ensure each
        // hole is wound opposite. Without this, GML data that stores both
        // exterior and interior with the same winding would produce a
        // self-intersecting keyhole bridge and the hole would be filled.
        double outerWindingSign = Math.signum(signedArea2D(resultScaled, projAxis));

        // Sort holes by their rightmost projected u (descending) so that the
        // easternmost hole bridges to the outer ring first and each subsequent
        // hole naturally bridges to a previously-bridged hole's vertex,
        // forming a chain. Without this, a hole stored mid-row in the GML
        // would bridge directly to the outer ring (because no other hole
        // exists yet in the merged ring), and later holes east of it would
        // fail to find a clean bridge target — falling back to stacking
        // multiple bridges on the same outer corner, which the ear-clip
        // fallback path then triangulates into hole-filling triangles.
        List<LinearRing> sortedHoles = new ArrayList<>(holes);
        sortedHoles.sort((a, b) -> Double.compare(maxProjectedU(b, scaleX, scaleY, projAxis),
                maxProjectedU(a, scaleX, scaleY, projAxis)));

        for (LinearRing hole : sortedHoles) {
            List<double[]> holePoints = toDoubleArray(hole.getPoints());
            if (holePoints.size() > 1) {
                holePoints.remove(holePoints.size() - 1);
            }
            if (holePoints.size() < 3) {
                continue;
            }
            List<double[]> holeScaled = scaleRing(holePoints, scaleX, scaleY);

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

            // Enforce opposite winding to outer in the projection plane.
            if (outerWindingSign != 0
                    && Math.signum(signedArea2D(holeScaled, projAxis)) == outerWindingSign) {
                Collections.reverse(holePoints);
                Collections.reverse(holeScaled);
                if (holeUVs != null) {
                    Collections.reverse(holeUVs);
                }
            }

            // Find the rightmost point of the hole in projected 2D.
            int holeIdx = 0;
            double maxU = Double.NEGATIVE_INFINITY;
            for (int i = 0; i < holeScaled.size(); i++) {
                double u = project2D(holeScaled.get(i), projAxis)[0];
                if (u > maxU) {
                    maxU = u;
                    holeIdx = i;
                }
            }

            int outerIdx = findClosestVisible(resultScaled, holeScaled,
                    holeScaled.get(holeIdx), projAxis);

            // Bridge: insert hole into outer ring at the connection point.
            // Maintain three parallel arrays (original positions, scaled
            // positions, UVs) so subsequent operations have all the data
            // they need.
            List<double[]> merged = new ArrayList<>(result.size() + holePoints.size() + 2);
            List<double[]> mergedScaled = new ArrayList<>(resultScaled.size() + holeScaled.size() + 2);
            List<float[]> mergedUV = uvResult != null
                    ? new ArrayList<>(result.size() + holePoints.size() + 2) : null;

            for (int i = 0; i <= outerIdx; i++) {
                merged.add(result.get(i));
                mergedScaled.add(resultScaled.get(i));
                if (mergedUV != null) mergedUV.add(uvResult.get(i));
            }
            for (int i = 0; i < holePoints.size(); i++) {
                int idx = (holeIdx + i) % holePoints.size();
                merged.add(holePoints.get(idx));
                mergedScaled.add(holeScaled.get(idx));
                if (mergedUV != null) {
                    mergedUV.add(holeUVs != null ? holeUVs.get(idx) : new float[]{0f, 0f});
                }
            }
            // Close the hole bridge
            merged.add(holePoints.get(holeIdx));
            mergedScaled.add(holeScaled.get(holeIdx));
            if (mergedUV != null) {
                mergedUV.add(holeUVs != null ? holeUVs.get(holeIdx) : new float[]{0f, 0f});
            }
            merged.add(result.get(outerIdx));
            mergedScaled.add(resultScaled.get(outerIdx));
            if (mergedUV != null) mergedUV.add(uvResult.get(outerIdx));

            for (int i = outerIdx + 1; i < result.size(); i++) {
                merged.add(result.get(i));
                mergedScaled.add(resultScaled.get(i));
                if (mergedUV != null) mergedUV.add(uvResult.get(i));
            }

            result = merged;
            resultScaled = mergedScaled;
            uvResult = mergedUV;
        }

        return new RingData(result, resultScaled, uvResult);
    }

    private static List<double[]> scaleRing(List<double[]> ring, double scaleX, double scaleY) {
        List<double[]> scaled = new ArrayList<>(ring.size());
        for (double[] pt : ring) {
            scaled.add(new double[]{pt[0] * scaleX, pt[1] * scaleY, pt[2]});
        }
        return scaled;
    }

    private static double signedArea2D(List<double[]> ring, int projAxis) {
        double area = 0;
        int n = ring.size();
        for (int i = 0; i < n; i++) {
            double[] a = project2D(ring.get(i), projAxis);
            double[] b = project2D(ring.get((i + 1) % n), projAxis);
            area += (b[0] - a[0]) * (b[1] + a[1]);
        }
        return area;
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
     * Distances and intersections are measured in the polygon's dominant 2D
     * projection so the test is valid on any plane (including vertical walls).
     * <p>
     * Vertices that are already bridge endpoints (appear more than once at the
     * same projected position in the merged ring) are skipped: connecting two
     * holes to the same vertex would visit it three times in the merged ring
     * and ear-clip would produce overlapping, hole-filling triangles.
     */
    private static int findClosestVisible(List<double[]> ringScaled,
                                          List<double[]> currentHoleScaled,
                                          double[] holePointScaled, int projAxis) {
        double[] hp2 = project2D(holePointScaled, projAxis);
        boolean[] isBridgeEndpoint = markBridgeEndpoints(ringScaled, projAxis);
        int closest = findClosestVisibleFiltered(ringScaled, currentHoleScaled, hp2,
                projAxis, isBridgeEndpoint);
        if (closest < 0) {
            // No non-bridge-endpoint candidate is visible — fall back to
            // allowing any vertex so we still produce something.
            closest = findClosestVisibleFiltered(ringScaled, currentHoleScaled, hp2,
                    projAxis, null);
        }
        return Math.max(closest, 0);
    }

    private static int findClosestVisibleFiltered(List<double[]> ringScaled,
                                                  List<double[]> currentHoleScaled, double[] hp2,
                                                  int projAxis, boolean[] skip) {
        double minDist = Double.MAX_VALUE;
        int closest = -1;
        for (int i = 0; i < ringScaled.size(); i++) {
            if (skip != null && skip[i]) {
                continue;
            }
            double[] r2 = project2D(ringScaled.get(i), projAxis);
            double dx = r2[0] - hp2[0];
            double dy = r2[1] - hp2[1];
            double dist = dx * dx + dy * dy;
            if (dist >= minDist) {
                continue;
            }
            if (bridgeCrossesEdge(ringScaled, hp2[0], hp2[1], r2[0], r2[1], i, projAxis)) {
                continue;
            }
            // Also reject candidates whose bridge passes through the hole
            // we are currently bridging in. Its edges are not yet part of
            // the merged ring, so the merged-ring check above misses them
            // — but a bridge that exits via the hole's far side would
            // self-intersect once the hole is inserted.
            if (bridgeCrossesHole(hp2[0], hp2[1], r2[0], r2[1], currentHoleScaled, projAxis)) {
                continue;
            }
            minDist = dist;
            closest = i;
        }
        return closest;
    }

    private static boolean bridgeCrossesHole(double px, double py, double qx, double qy,
                                             List<double[]> holeScaled, int projAxis) {
        int n = holeScaled.size();
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            double[] a2 = project2D(holeScaled.get(i), projAxis);
            double[] b2 = project2D(holeScaled.get(j), projAxis);
            if (segmentsIntersectStrict(px, py, qx, qy, a2[0], a2[1], b2[0], b2[1])) {
                return true;
            }
        }
        return false;
    }

    private static boolean[] markBridgeEndpoints(List<double[]> ringScaled, int projAxis) {
        int n = ringScaled.size();
        boolean[] flagged = new boolean[n];
        double[][] projected = new double[n][];
        for (int i = 0; i < n; i++) {
            projected[i] = project2D(ringScaled.get(i), projAxis);
        }
        for (int i = 0; i < n; i++) {
            if (flagged[i]) continue;
            for (int j = i + 1; j < n; j++) {
                if (coincident2D(projected[i], projected[j])) {
                    flagged[i] = true;
                    flagged[j] = true;
                }
            }
        }
        return flagged;
    }

    /**
     * Check if the bridge segment (px,py)→(qx,qy) crosses any edge of the ring,
     * excluding edges that share the vertex at {@code skipVertex}. Inputs are
     * already in the polygon's 2D projection.
     */
    private static boolean bridgeCrossesEdge(List<double[]> ringScaled,
                                             double px, double py, double qx, double qy,
                                             int skipVertex, int projAxis) {
        int n = ringScaled.size();
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            if (i == skipVertex || j == skipVertex) continue;

            double[] a2 = project2D(ringScaled.get(i), projAxis);
            double[] b2 = project2D(ringScaled.get(j), projAxis);

            if (segmentsIntersectStrict(px, py, qx, qy, a2[0], a2[1], b2[0], b2[1])) {
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
                    // Skip points that are coincident with one of the
                    // candidate triangle's corners — keyhole hole-bridging
                    // intentionally produces such duplicate vertices, and
                    // counting them as "inside" would block legitimate ears
                    // and leave the hole filled.
                    if (coincident2D(p, a) || coincident2D(p, b) || coincident2D(p, c)) {
                        continue;
                    }
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
                            // Containment check: skip if any other vertex is inside.
                            // Coincident-with-corner points are bridge duplicates;
                            // ignore them (see ear-loop above for rationale).
                            boolean valid = true;
                            for (int j = 0; j < indices.size(); j++) {
                                if (j == prevIdx || j == i || j == nextIdx) continue;
                                double[] p = project2D(vertices.get(indices.get(j)), projAxis);
                                if (coincident2D(p, a) || coincident2D(p, b) || coincident2D(p, c)) {
                                    continue;
                                }
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

    private static boolean coincident2D(double[] a, double[] b) {
        return Math.abs(a[0] - b[0]) < TOLERANCE && Math.abs(a[1] - b[1]) < TOLERANCE;
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
        return signedArea2D(ring, projAxis) < 0;
    }

    private static List<double[]> toDoubleArray(List<Coordinate> coordinates) {
        List<double[]> result = new ArrayList<>(coordinates.size());
        for (Coordinate c : coordinates) {
            result.add(new double[]{c.getX(), c.getY(), c.getZ()});
        }
        return result;
    }
}
