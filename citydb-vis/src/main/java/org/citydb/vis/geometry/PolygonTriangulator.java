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
import java.util.BitSet;
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
 * This class owns polygon collection/dedup, plane derivation (normal +
 * dominant projection axis), winding normalization, and mesh emission; the
 * two algorithmic steps are delegated to package-private companions —
 * {@link HoleBridger} merges interior rings into the outer ring via keyhole
 * bridging, and {@link EarClipper} triangulates the resulting single ring.
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
    // When true, input coordinates are local Cartesian meters (implicit-geometry
    // prototype templates) instead of EPSG:4326, so the degree-to-meter scaling
    // becomes the identity. All geometric tests already run in the scaled
    // (metric) space, so this is the only switch the two unit models need.
    private final boolean localMeters;

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

    // Source-shape statistics across all triangulate() calls on this
    // instance (one feature). A "triangle polygon" is a hole-free polygon
    // whose exterior ring has exactly three distinct vertices — the storage
    // pattern of pre-triangulated mesh data (BIM conversions, TINs stored
    // as many small polygons). Only polygons accepted for triangulation are
    // counted; degenerate rings rejected by the early-outs stay out of the
    // statistics. GeometryMeshBuilder uses the all-triangles signal to
    // demote the oversized-mesh T-junction warning to DEBUG for such data.
    private int sourcePolygonCount;
    private int triangleSourcePolygonCount;

    public PolygonTriangulator(boolean localMeters) {
        this.localMeters = localMeters;
    }

    public TriangleMesh triangulate(Geometry<?> geometry, long featureId, Name surfaceType,
                                    RingAttributes ringAttributes) {
        TriangleMesh mesh = new TriangleMesh();
        List<Polygon> polygons = collectPolygons(geometry);

        for (Polygon polygon : polygons) {
            triangulatePolygon(polygon, featureId, surfaceType, mesh, ringAttributes);
        }

        return mesh;
    }

    /** Number of non-degenerate source polygons triangulated so far. */
    public int getSourcePolygonCount() {
        return sourcePolygonCount;
    }

    /** Number of those source polygons that are hole-free triangles. */
    public int getTriangleSourcePolygonCount() {
        return triangleSourcePolygonCount;
    }

    /**
     * Whether every source polygon triangulated so far is a hole-free
     * triangle — the signature of pre-triangulated mesh data (BIM-converted
     * buildings, TINs stored as many small polygons). False when no polygon
     * has been triangulated yet.
     */
    public boolean isSourcePreTriangulated() {
        return sourcePolygonCount > 0 && triangleSourcePolygonCount == sourcePolygonCount;
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

    private void triangulatePolygon(Polygon polygon, long featureId, Name surfaceType,
                                    TriangleMesh mesh, RingAttributes ringAttributes) {
        // Unpack the per-ring appearance lookups (any may be null when the
        // feature carries no data of that kind).
        Map<LinearRing, List<TextureCoordinate>> texCoordMap = ringAttributes.texCoords();
        Map<LinearRing, Integer> ringTextureMap = ringAttributes.ringTextureIds();
        Map<LinearRing, float[]> ringColorMap = ringAttributes.ringColors();

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

        // Compute centroid latitude for degree-to-meter conversion; identity
        // when the input is already in local Cartesian meters.
        double scaleX, scaleY;
        if (localMeters) {
            scaleX = 1.0;
            scaleY = 1.0;
        } else {
            double centroidLat = 0;
            for (Coordinate c : outerPoints) {
                centroidLat += c.getY();
            }
            centroidLat /= outerPoints.size();
            scaleX = GeoTransform.metersPerDegreeLon(centroidLat);
            scaleY = GeoTransform.WGS84_METERS_PER_DEGREE_LAT;
        }

        // Build the outer ring (without the closing duplicate vertex) in
        // both original (degrees/meters) and scaled (all-meters) form.
        List<double[]> outerRing = RingMathUtil.toDoubleArray(outerPoints);
        if (outerRing.size() > 1) {
            outerRing.remove(outerRing.size() - 1);
        }
        if (outerRing.size() < 3) {
            return;
        }
        List<double[]> outerScaled = RingMathUtil.scaleRing(outerRing, scaleX, scaleY);

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

        // Past every degeneracy early-out: the polygon will be triangulated,
        // so it enters the source-shape statistics.
        sourcePolygonCount++;
        if (outerRing.size() == 3 && !polygon.hasInteriorRings()) {
            triangleSourcePolygonCount++;
        }

        List<float[]> outerUVRing = null;
        if (hasUV) {
            outerUVRing = RingMathUtil.toUVArray(outerTexCoords);
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
        // Boundary-edge flags of the (possibly bridged) ring: flag i covers
        // edge i -> (i + 1) % n. A null value means "every edge is a boundary
        // edge" — the hole-free case, where no bridges exist. Feeds the
        // per-triangle outline-edge mask (CESIUM_primitive_outline).
        BitSet boundaryEdges;
        if (polygon.hasInteriorRings()) {
            HoleBridger.BridgedRing bridged = HoleBridger.bridge(outerRing, outerScaled,
                    outerUVRing, polygon.getInteriorRings(), texCoordMap, scaleX, scaleY, projAxis);
            ring = bridged.positions();
            scaledRing = bridged.scaledPositions();
            uvRing = bridged.uvs();
            boundaryEdges = bridged.boundaryEdges();
        } else {
            ring = outerRing;
            scaledRing = outerScaled;
            uvRing = outerUVRing;
            boundaryEdges = null;
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
            if (boundaryEdges != null) {
                boundaryEdges = reverseEdgeFlags(boundaryEdges, ring.size());
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
        List<int[]> triangleIndices = EarClipper.clip(scaledRing, projAxis);

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
        // The outline-edge mask is computed on the FINAL emitted vertex order
        // (after the winding swap): outlineMask tests ring adjacency in both
        // directions, so no bit permutation is needed for reversed polygons.
        boolean polyColored = polyColor != null;
        int ringSize = ring.size();
        for (int[] tri : triangleIndices) {
            int a = tri[0];
            int b = reverseWinding ? tri[2] : tri[1];
            int c = reverseWinding ? tri[1] : tri[2];
            byte outlineEdges = outlineMask(a, b, c, ringSize, boundaryEdges);
            mesh.addTriangle(
                    baseVertex + a,
                    baseVertex + b,
                    baseVertex + c,
                    featureId, polyTextureId, polyColored, surfaceType, outlineEdges);
        }
    }

    /**
     * Reverse the per-edge boundary flags alongside a
     * {@link Collections#reverse} of the ring vertices. Old edge
     * {@code i -> i+1} becomes the edge leaving new position {@code n-2-i}
     * (mod n), so {@code newFlag[j] = oldFlag[(n - 2 - j) mod n]} — the old
     * closing edge {@code n-1 -> 0} stays at new index {@code n-1}.
     */
    private static BitSet reverseEdgeFlags(BitSet flags, int n) {
        BitSet reversed = new BitSet(n);
        for (int j = 0; j < n; j++) {
            if (flags.get(((n - 2 - j) % n + n) % n)) {
                reversed.set(j);
            }
        }
        return reversed;
    }

    /**
     * Outline-edge mask for the emitted triangle {@code (a, b, c)} (indices
     * into the triangulated ring): a bit is set iff the edge connects two
     * ring-adjacent vertices AND that ring edge is a polygon boundary edge
     * (not a keyhole bridge). Triangulation diagonals connect non-adjacent
     * ring vertices and stay unset. {@code boundaryEdges == null} means the
     * hole-free case where every ring edge is a boundary edge.
     */
    private static byte outlineMask(int a, int b, int c, int n, BitSet boundaryEdges) {
        byte mask = 0;
        if (isBoundaryEdge(a, b, n, boundaryEdges)) {
            mask |= TriangleMesh.OUTLINE_EDGE_01;
        }
        if (isBoundaryEdge(b, c, n, boundaryEdges)) {
            mask |= TriangleMesh.OUTLINE_EDGE_12;
        }
        if (isBoundaryEdge(c, a, n, boundaryEdges)) {
            mask |= TriangleMesh.OUTLINE_EDGE_20;
        }
        return mask;
    }

    /**
     * Whether the undirected triangle edge {@code (a, b)} coincides with a
     * boundary ring edge. Adjacency is tested in both ring directions so the
     * result is independent of the triangle's final winding.
     */
    private static boolean isBoundaryEdge(int a, int b, int n, BitSet boundaryEdges) {
        if ((a + 1) % n == b) {
            return boundaryEdges == null || boundaryEdges.get(a);
        }
        if ((b + 1) % n == a) {
            return boundaryEdges == null || boundaryEdges.get(b);
        }
        return false;
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
        if (length < RingMathUtil.TOLERANCE) {
            return new float[]{0, 0, 0};
        }

        return new float[]{(float) (nx / length), (float) (ny / length), (float) (nz / length)};
    }

    private static boolean isZeroVector(float[] v) {
        return Math.abs(v[0]) < RingMathUtil.TOLERANCE && Math.abs(v[1]) < RingMathUtil.TOLERANCE
                && Math.abs(v[2]) < RingMathUtil.TOLERANCE;
    }

    private static int getDominantAxis(float[] normal) {
        float absX = Math.abs(normal[0]);
        float absY = Math.abs(normal[1]);
        float absZ = Math.abs(normal[2]);

        if (absX >= absY && absX >= absZ) return 0;
        if (absY >= absX && absY >= absZ) return 1;
        return 2;
    }

    private static boolean isCCW(List<double[]> ring, int projAxis) {
        return RingMathUtil.signedArea2D(ring, projAxis) < 0;
    }
}
