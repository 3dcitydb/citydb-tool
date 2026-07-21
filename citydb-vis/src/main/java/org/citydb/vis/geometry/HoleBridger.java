/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.geometry;

import org.citydb.model.appearance.TextureCoordinate;
import org.citydb.model.geometry.Coordinate;
import org.citydb.model.geometry.LinearRing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Merges a polygon's interior rings (holes) into its outer ring via keyhole
 * bridging, producing a single ring that {@link EarClipper} can triangulate.
 * All visibility and intersection tests run in the polygon's dominant 2D
 * projection so bridging works on every plane, including vertical walls.
 * <p>
 * Maintains three parallel ring representations throughout: original
 * coordinates (degrees/meters, what the mesh emits), scaled coordinates
 * (all-meters, what the geometric tests use), and optional UVs.
 * <p>
 * Package-private companion of {@link PolygonTriangulator}, which computes
 * the projection axis from the outer ring before delegating here.
 */
final class HoleBridger {
    private HoleBridger() {
    }

    /**
     * The merged ring in all three parallel representations; {@code uvs} is
     * {@code null} when the polygon carries no texture coordinates.
     */
    record BridgedRing(List<double[]> positions, List<double[]> scaledPositions,
                       List<float[]> uvs) {
    }

    static BridgedRing bridge(
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
        double outerWindingSign = Math.signum(RingMathUtil.signedArea2D(resultScaled, projAxis));

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
            List<double[]> holePoints = RingMathUtil.toDoubleArray(hole.getPoints());
            if (holePoints.size() > 1) {
                holePoints.remove(holePoints.size() - 1);
            }
            if (holePoints.size() < 3) {
                continue;
            }
            List<double[]> holeScaled = RingMathUtil.scaleRing(holePoints, scaleX, scaleY);

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
                    holeUVs = RingMathUtil.toUVArray(holeTexCoords);
                    if (holeUVs.size() > 1) {
                        holeUVs.remove(holeUVs.size() - 1);
                    }
                }
            }

            // Enforce opposite winding to outer in the projection plane.
            if (outerWindingSign != 0
                    && Math.signum(RingMathUtil.signedArea2D(holeScaled, projAxis)) == outerWindingSign) {
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
                double u = RingMathUtil.project2D(holeScaled.get(i), projAxis)[0];
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

        return new BridgedRing(result, resultScaled, uvResult);
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
            double u = RingMathUtil.project2D(scaled, projAxis)[0];
            if (u > max) max = u;
        }
        return max;
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
        double[] hp2 = RingMathUtil.project2D(holePointScaled, projAxis);
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
            double[] r2 = RingMathUtil.project2D(ringScaled.get(i), projAxis);
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
            double[] a2 = RingMathUtil.project2D(holeScaled.get(i), projAxis);
            double[] b2 = RingMathUtil.project2D(holeScaled.get(j), projAxis);
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
            projected[i] = RingMathUtil.project2D(ringScaled.get(i), projAxis);
        }
        for (int i = 0; i < n; i++) {
            if (flagged[i]) continue;
            for (int j = i + 1; j < n; j++) {
                if (RingMathUtil.coincident2D(projected[i], projected[j])) {
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

            double[] a2 = RingMathUtil.project2D(ringScaled.get(i), projAxis);
            double[] b2 = RingMathUtil.project2D(ringScaled.get(j), projAxis);

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
}
