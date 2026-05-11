/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.geometry;

import org.citydb.model.common.Name;
import org.citydb.vis.util.BoundingBoxUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class TriangleMesh {
    private static final Logger logger = LoggerFactory.getLogger(TriangleMesh.class);
    private static final float[] WHITE_RGBA = {1f, 1f, 1f, 1f};

    // Ceiling on triangle count for the T-junction pass. Below it the
    // spatial-index detection plus the split-application loop finish in
    // milliseconds for normal city-scale features. Above it (BIM-scale
    // meshes from deeply nested feature trees) the dense overlapping
    // geometry both makes detection scan minutes long and produces splits
    // that would weld topologically independent components — the wrong
    // thing to do. Skip with a warning.
    private static final int T_JUNCTION_MAX_TRIANGLES = 100_000;

    private final List<double[]> positions;
    private final List<float[]> normals;
    private final List<float[]> texCoords;
    private final List<float[]> colors;
    private final List<int[]> triangles;
    private final List<Long> featureIds;
    private final List<Integer> triangleTextureIds;
    // Per-triangle bit: set when the triangle's source polygon carried an
    // explicit X3DMaterial color (so its vertex COLOR_0 values are authored,
    // not WHITE_RGBA padding from merge/mixed-feature processing). Drives the
    // GLB writer's untextured-plain vs untextured-unlit primitive split so
    // unappeared surfaces in a colored feature still render with PBR shading.
    private final BitSet triangleColored;
    // Per-triangle source surface feature type — the most-specific Feature
    // ancestor of the triangle's polygon (e.g. RoofSurface / WallSurface for
    // a CityGML 3.0 Building, falling back to the top-level Feature when the
    // geometry is not nested in a boundary surface). Drives per-feature-type
    // styling on the 3D Tiles plain path so a single building can render
    // each surface with a different color. Always non-null when filled in.
    private final List<Name> triangleSurfaceTypes;
    private boolean hasTexCoords;
    private boolean hasColors;

    public TriangleMesh() {
        positions = new ArrayList<>();
        normals = new ArrayList<>();
        texCoords = new ArrayList<>();
        colors = new ArrayList<>();
        triangles = new ArrayList<>();
        featureIds = new ArrayList<>();
        triangleTextureIds = new ArrayList<>();
        triangleColored = new BitSet();
        triangleSurfaceTypes = new ArrayList<>();
    }

    public List<double[]> getPositions() {
        return Collections.unmodifiableList(positions);
    }

    public List<float[]> getNormals() {
        return Collections.unmodifiableList(normals);
    }

    public List<int[]> getTriangles() {
        return Collections.unmodifiableList(triangles);
    }

    public List<float[]> getTexCoords() {
        return Collections.unmodifiableList(texCoords);
    }

    public boolean hasTexCoords() {
        return hasTexCoords;
    }

    public TriangleMesh setHasTexCoords(boolean hasTexCoords) {
        this.hasTexCoords = hasTexCoords;
        return this;
    }

    public List<float[]> getColors() {
        return Collections.unmodifiableList(colors);
    }

    public boolean hasColors() {
        return hasColors;
    }

    public List<Long> getFeatureIds() {
        return Collections.unmodifiableList(featureIds);
    }

    public int getVertexCount() {
        return positions.size();
    }

    public int getTriangleCount() {
        return triangles.size();
    }

    public boolean isEmpty() {
        return triangles.isEmpty();
    }

    public int addVertex(double x, double y, double z, float nx, float ny, float nz) {
        int index = positions.size();
        positions.add(new double[]{x, y, z});
        normals.add(new float[]{nx, ny, nz});
        // If the mesh already has UVs/colors, pad this vertex so the
        // texCoords.size()/colors.size() == positions.size() invariant holds.
        if (hasTexCoords) {
            texCoords.add(new float[]{0f, 0f});
        }
        if (hasColors) {
            colors.add(WHITE_RGBA.clone());
        }
        return index;
    }

    public int addVertex(double x, double y, double z, float nx, float ny, float nz,
                         float u, float v) {
        int index = positions.size();
        positions.add(new double[]{x, y, z});
        normals.add(new float[]{nx, ny, nz});
        // First UV vertex for this mesh: backfill any earlier non-UV vertices
        // with {0,0} so the texCoords.size() == positions.size() invariant holds.
        if (!hasTexCoords) {
            while (texCoords.size() < index) {
                texCoords.add(new float[]{0f, 0f});
            }
            hasTexCoords = true;
        }
        texCoords.add(new float[]{u, v});
        if (hasColors) {
            colors.add(WHITE_RGBA.clone());
        }
        return index;
    }

    public int addVertex(double x, double y, double z, float nx, float ny, float nz,
                         float r, float g, float b, float a) {
        int index = positions.size();
        positions.add(new double[]{x, y, z});
        normals.add(new float[]{nx, ny, nz});
        if (hasTexCoords) {
            texCoords.add(new float[]{0f, 0f});
        }
        if (!hasColors) {
            while (colors.size() < index) {
                colors.add(WHITE_RGBA.clone());
            }
            hasColors = true;
        }
        colors.add(new float[]{r, g, b, a});
        return index;
    }

    public int addVertex(double x, double y, double z, float nx, float ny, float nz,
                         float u, float v, float r, float g, float b, float a) {
        int index = positions.size();
        positions.add(new double[]{x, y, z});
        normals.add(new float[]{nx, ny, nz});
        if (!hasTexCoords) {
            while (texCoords.size() < index) {
                texCoords.add(new float[]{0f, 0f});
            }
            hasTexCoords = true;
        }
        texCoords.add(new float[]{u, v});
        if (!hasColors) {
            while (colors.size() < index) {
                colors.add(WHITE_RGBA.clone());
            }
            hasColors = true;
        }
        colors.add(new float[]{r, g, b, a});
        return index;
    }

    public void addTriangle(int v0, int v1, int v2, long featureId, int textureId,
                            boolean colored, Name surfaceType) {
        int triIndex = triangles.size();
        triangles.add(new int[]{v0, v1, v2});
        featureIds.add(featureId);
        triangleTextureIds.add(textureId);
        if (colored) {
            triangleColored.set(triIndex);
        }
        triangleSurfaceTypes.add(surfaceType);
    }

    /**
     * Source surface feature type of the triangle at {@code triIndex} — see
     * {@link #triangleSurfaceTypes} for semantics. Never {@code null} on
     * meshes built through the standard pipeline; tests that synthesize a
     * mesh directly should pass a non-null type to {@link #addTriangle}.
     */
    public Name getTriangleSurfaceType(int triIndex) {
        return triangleSurfaceTypes.get(triIndex);
    }

    public List<Integer> getTriangleTextureIds() {
        return Collections.unmodifiableList(triangleTextureIds);
    }

    /**
     * Whether the triangle at the given index came from an X3DMaterial-colored
     * polygon. Always {@code false} when {@link #hasColors()} is {@code false}.
     */
    public boolean isTriangleColored(int triIndex) {
        return triangleColored.get(triIndex);
    }

    public void merge(TriangleMesh other) {
        int offset = positions.size();
        int otherSize = other.positions.size();
        positions.addAll(other.positions);
        normals.addAll(other.normals);

        // Maintain the invariant: when hasTexCoords is true,
        // texCoords.size() == positions.size(). Pad with {0,0} on either side
        // when mixing textured and untextured meshes within a single node.
        if (hasTexCoords || other.hasTexCoords) {
            while (texCoords.size() < offset) {
                texCoords.add(new float[]{0f, 0f});
            }
            if (other.hasTexCoords) {
                texCoords.addAll(other.texCoords);
            } else {
                for (int i = 0; i < otherSize; i++) {
                    texCoords.add(new float[]{0f, 0f});
                }
            }
            hasTexCoords = true;
        }

        // Same invariant for colors: pad missing side with white so colored
        // and uncolored sub-meshes can coexist within one node.
        if (hasColors || other.hasColors) {
            while (colors.size() < offset) {
                colors.add(WHITE_RGBA.clone());
            }
            if (other.hasColors) {
                colors.addAll(other.colors);
            } else {
                for (int i = 0; i < otherSize; i++) {
                    colors.add(WHITE_RGBA.clone());
                }
            }
            hasColors = true;
        }

        int triOffset = triangles.size();
        for (int[] tri : other.triangles) {
            triangles.add(new int[]{tri[0] + offset, tri[1] + offset, tri[2] + offset});
        }
        featureIds.addAll(other.featureIds);
        triangleTextureIds.addAll(other.triangleTextureIds);
        triangleSurfaceTypes.addAll(other.triangleSurfaceTypes);

        // Shift other.triangleColored bits by triOffset and OR into ours.
        // BitSet has no built-in shift, so iterate set bits explicitly.
        for (int bit = other.triangleColored.nextSetBit(0); bit >= 0;
                bit = other.triangleColored.nextSetBit(bit + 1)) {
            triangleColored.set(bit + triOffset);
        }
    }

    /**
     * Resolve T-junction vertices by splitting affected triangle edges.
     * A T-junction occurs when a vertex lies on an edge of another triangle
     * without being a vertex of that triangle, causing sub-pixel rendering cracks.
     * <p>
     * Uses a uniform 3D hash grid over triangle edges so each vertex tests only
     * the candidate edges in its local cell rather than every triangle in the
     * mesh. Complexity is O(T) to build the index plus O(V·k) for the scan,
     * where k is the average bucket size — small for typical building geometry.
     * <p>
     * Above {@value #T_JUNCTION_MAX_TRIANGLES} triangles the pass is skipped
     * with a warning. At that scale (deeply nested BIM features merged into a
     * single mesh) the split-application loop becomes a runaway cost, and the
     * resulting topology changes would weld topologically independent
     * components into shared edges — more harmful than the sub-pixel cracks
     * the pass is meant to fix.
     *
     * @param scaleX degrees-to-meters scale for X (longitude)
     * @param scaleY degrees-to-meters scale for Y (latitude)
     * @param toleranceMeters distance threshold in meters; the grid cell size
     *                        is tuned for tolerance values in the few-centimetre
     *                        range
     */
    public void resolveTJunctions(double scaleX, double scaleY, double toleranceMeters) {
        if (positions.size() < 3 || triangles.isEmpty()) return;

        if (triangles.size() > T_JUNCTION_MAX_TRIANGLES) {
            logger.warn("Skipping T-junction resolution for oversized feature "
                    + "mesh (triangles={} > {}). Sub-pixel cracks at shared "
                    + "edges (if any) will not be resolved.",
                    triangles.size(), T_JUNCTION_MAX_TRIANGLES);
            return;
        }

        double tol2 = toleranceMeters * toleranceMeters;
        int maxIterations = 5;

        for (int iter = 0; iter < maxIterations; iter++) {
            int vertexCount = positions.size();
            double[][] mPos = new double[vertexCount][3];
            for (int i = 0; i < vertexCount; i++) {
                double[] p = positions.get(i);
                mPos[i][0] = p[0] * scaleX;
                mPos[i][1] = p[1] * scaleY;
                mPos[i][2] = p[2];
            }

            int triCount = triangles.size();
            TJunctionEdgeGrid edgeGrid = TJunctionEdgeGrid.build(mPos, triangles, triCount);

            // Find all T-junctions: vertex vi lies on edge of triangle ti.
            // Store the parametric position t along the edge for correct UV interpolation.
            List<int[]> splits = new ArrayList<>();   // {triIndex, edgeSlot, tJunctionVertex}
            List<Double> splitParams = new ArrayList<>(); // parametric t for each split

            // Per-vertex dedup of edges already tested in this vertex's cell
            // sweep (the same edge can appear in multiple adjacent cells, and
            // shared edges across triangles appear under different edgeIds).
            // Stamping (increment the marker once per vertex, compare against
            // it) costs O(1) per check and avoids the per-vertex clear that
            // dominates BitSet/HashSet alternatives on BIM-scale meshes.
            int[] visitedStamp = new int[triCount * 3];
            int currentStamp = 0;

            for (int vi = 0; vi < vertexCount; vi++) {
                double vx = mPos[vi][0], vy = mPos[vi][1], vz = mPos[vi][2];
                int cxMin = TJunctionEdgeGrid.cellOf(vx - toleranceMeters);
                int cxMax = TJunctionEdgeGrid.cellOf(vx + toleranceMeters);
                int cyMin = TJunctionEdgeGrid.cellOf(vy - toleranceMeters);
                int cyMax = TJunctionEdgeGrid.cellOf(vy + toleranceMeters);
                int czMin = TJunctionEdgeGrid.cellOf(vz - toleranceMeters);
                int czMax = TJunctionEdgeGrid.cellOf(vz + toleranceMeters);

                currentStamp++;
                for (int cx = cxMin; cx <= cxMax; cx++) {
                    for (int cy = cyMin; cy <= cyMax; cy++) {
                        for (int cz = czMin; cz <= czMax; cz++) {
                            TJunctionEdgeGrid.IntList bucket = edgeGrid.bucket(cx, cy, cz);
                            if (bucket == null) continue;
                            for (int bi = 0; bi < bucket.size; bi++) {
                                int edgeId = bucket.data[bi];
                                if (visitedStamp[edgeId] == currentStamp) continue;
                                visitedStamp[edgeId] = currentStamp;

                                int ti = edgeId / 3;
                                int e = edgeId % 3;
                                int[] tri = triangles.get(ti);
                                if (vi == tri[0] || vi == tri[1] || vi == tri[2]) continue;

                                int ei1 = tri[e];
                                int ei2 = tri[(e + 1) % 3];
                                double dx = mPos[ei2][0] - mPos[ei1][0];
                                double dy = mPos[ei2][1] - mPos[ei1][1];
                                double dz = mPos[ei2][2] - mPos[ei1][2];
                                double edgeLen2 = dx * dx + dy * dy + dz * dz;
                                if (edgeLen2 < 1e-10) continue;

                                double t = ((vx - mPos[ei1][0]) * dx
                                        + (vy - mPos[ei1][1]) * dy
                                        + (vz - mPos[ei1][2]) * dz) / edgeLen2;
                                if (t <= 0.001 || t >= 0.999) continue;

                                double px = mPos[ei1][0] + t * dx;
                                double py = mPos[ei1][1] + t * dy;
                                double pz = mPos[ei1][2] + t * dz;
                                double dist2 = (vx - px) * (vx - px)
                                        + (vy - py) * (vy - py)
                                        + (vz - pz) * (vz - pz);

                                if (dist2 < tol2) {
                                    splits.add(new int[]{ti, e, vi});
                                    splitParams.add(t);
                                }
                            }
                        }
                    }
                }
            }

            if (splits.isEmpty()) break;

            // BitSet (primitive bit-per-triangle) rather than HashSet<Integer>
            // — splits can hold millions of entries on dense meshes near the
            // size threshold, and the per-entry Integer.box + HashMap.contains
            // cost dominated the loop in profiling.
            BitSet removed = new BitSet(triCount);
            List<int[]> newTriangles = new ArrayList<>();
            List<Long> newFeatureIds = new ArrayList<>();
            List<Integer> newTriTexIds = new ArrayList<>();
            // Per-new-triangle colored flag, inherited from the parent triangle
            // being split. Both sub-triangles share the parent's flag.
            List<Boolean> newColored = new ArrayList<>();
            // Per-new-triangle source surface type, also inherited from the
            // parent triangle (a single split never crosses a surface boundary).
            List<Name> newSurfaceTypes = new ArrayList<>();

            for (int s = 0; s < splits.size(); s++) {
                int[] split = splits.get(s);
                int ti = split[0], edgeSlot = split[1], vi = split[2];
                if (removed.get(ti)) continue;

                int[] tri = triangles.get(ti);
                int ei1 = tri[edgeSlot];
                int ei2 = tri[(edgeSlot + 1) % 3];
                int ei3 = tri[(edgeSlot + 2) % 3];
                long fid = featureIds.get(ti);
                int texId = triangleTextureIds.get(ti);
                boolean colored = triangleColored.get(ti);
                Name surfaceType = triangleSurfaceTypes.get(ti);

                // New vertex at vi's position with the split triangle's normal.
                // Interpolate UV/color along the edge at the parametric position t.
                // Assumes per-face normals (all edge vertices share the same
                // normal, as emitted by PolygonTriangulator). If upstream ever
                // produces per-vertex smooth normals, this should interpolate.
                float[] triNormal = normals.get(ei1);
                double[] viPos = positions.get(vi);
                float tParam = (float) splitParams.get(s).doubleValue();
                int newVi;
                if (hasTexCoords && hasColors) {
                    float[] uv1 = texCoords.get(ei1), uv2 = texCoords.get(ei2);
                    float[] c1 = colors.get(ei1), c2 = colors.get(ei2);
                    newVi = addVertex(viPos[0], viPos[1], viPos[2],
                            triNormal[0], triNormal[1], triNormal[2],
                            uv1[0] + tParam * (uv2[0] - uv1[0]),
                            uv1[1] + tParam * (uv2[1] - uv1[1]),
                            c1[0] + tParam * (c2[0] - c1[0]),
                            c1[1] + tParam * (c2[1] - c1[1]),
                            c1[2] + tParam * (c2[2] - c1[2]),
                            c1[3] + tParam * (c2[3] - c1[3]));
                } else if (hasTexCoords) {
                    float[] uv1 = texCoords.get(ei1), uv2 = texCoords.get(ei2);
                    newVi = addVertex(viPos[0], viPos[1], viPos[2],
                            triNormal[0], triNormal[1], triNormal[2],
                            uv1[0] + tParam * (uv2[0] - uv1[0]),
                            uv1[1] + tParam * (uv2[1] - uv1[1]));
                } else if (hasColors) {
                    float[] c1 = colors.get(ei1), c2 = colors.get(ei2);
                    newVi = addVertex(viPos[0], viPos[1], viPos[2],
                            triNormal[0], triNormal[1], triNormal[2],
                            c1[0] + tParam * (c2[0] - c1[0]),
                            c1[1] + tParam * (c2[1] - c1[1]),
                            c1[2] + tParam * (c2[2] - c1[2]),
                            c1[3] + tParam * (c2[3] - c1[3]));
                } else {
                    newVi = addVertex(viPos[0], viPos[1], viPos[2],
                            triNormal[0], triNormal[1], triNormal[2]);
                }

                removed.set(ti);
                newTriangles.add(new int[]{ei1, newVi, ei3});
                newFeatureIds.add(fid);
                newTriTexIds.add(texId);
                newColored.add(colored);
                newSurfaceTypes.add(surfaceType);
                newTriangles.add(new int[]{newVi, ei2, ei3});
                newFeatureIds.add(fid);
                newTriTexIds.add(texId);
                newColored.add(colored);
                newSurfaceTypes.add(surfaceType);
            }

            List<int[]> updatedTri = new ArrayList<>();
            List<Long> updatedFid = new ArrayList<>();
            List<Integer> updatedTexId = new ArrayList<>();
            BitSet updatedColored = new BitSet();
            List<Name> updatedSurfaceTypes = new ArrayList<>();
            int outIdx = 0;
            for (int ti = 0; ti < triCount; ti++) {
                if (!removed.get(ti)) {
                    updatedTri.add(triangles.get(ti));
                    updatedFid.add(featureIds.get(ti));
                    updatedTexId.add(triangleTextureIds.get(ti));
                    if (triangleColored.get(ti)) {
                        updatedColored.set(outIdx);
                    }
                    updatedSurfaceTypes.add(triangleSurfaceTypes.get(ti));
                    outIdx++;
                }
            }
            updatedTri.addAll(newTriangles);
            updatedFid.addAll(newFeatureIds);
            updatedTexId.addAll(newTriTexIds);
            for (Boolean c : newColored) {
                if (c) {
                    updatedColored.set(outIdx);
                }
                outIdx++;
            }
            updatedSurfaceTypes.addAll(newSurfaceTypes);

            triangles.clear();
            triangles.addAll(updatedTri);
            featureIds.clear();
            featureIds.addAll(updatedFid);
            triangleTextureIds.clear();
            triangleTextureIds.addAll(updatedTexId);
            triangleColored.clear();
            triangleColored.or(updatedColored);
            triangleSurfaceTypes.clear();
            triangleSurfaceTypes.addAll(updatedSurfaceTypes);
        }
    }

    /**
     * Key for duplicate-triangle detection: three position hashes, compared
     * by value. Using a record gives auto-generated equals/hashCode that
     * compare all three 64-bit fields. Hash collisions in {@link #vertexHash}
     * are theoretically possible but vanishingly unlikely for real geographic
     * coordinate data (birthday-paradox threshold ~4 * 10^9 distinct vertices).
     */
    private record TriangleKey(long h0, long h1, long h2) {}

    /**
     * Remove duplicate triangles that occupy the same space (same vertex positions
     * regardless of winding order). Duplicates cause Z-fighting artifacts.
     * <p>
     * Dedup is exact on {@code Double.doubleToLongBits} — downstream vertex
     * welding in the geometry encoder handles near-duplicates within 2 cm,
     * so upstream rounding would be both redundant and incorrect.
     */
    public void removeDuplicateTriangles() {
        if (triangles.size() <= 1) return;

        Set<TriangleKey> seen = new HashSet<>();
        List<int[]> kept = new ArrayList<>();
        List<Long> keptIds = new ArrayList<>();
        List<Integer> keptTexIds = new ArrayList<>();
        BitSet keptColored = new BitSet();
        List<Name> keptSurfaceTypes = new ArrayList<>();

        for (int i = 0; i < triangles.size(); i++) {
            int[] tri = triangles.get(i);
            long h0 = vertexHash(positions.get(tri[0]));
            long h1 = vertexHash(positions.get(tri[1]));
            long h2 = vertexHash(positions.get(tri[2]));

            // Sort ascending so the key is winding-order-independent.
            if (h0 > h1) { long t = h0; h0 = h1; h1 = t; }
            if (h1 > h2) { long t = h1; h1 = h2; h2 = t; }
            if (h0 > h1) { long t = h0; h0 = h1; h1 = t; }

            if (seen.add(new TriangleKey(h0, h1, h2))) {
                if (triangleColored.get(i)) {
                    keptColored.set(kept.size());
                }
                kept.add(tri);
                keptIds.add(featureIds.get(i));
                keptTexIds.add(triangleTextureIds.get(i));
                keptSurfaceTypes.add(triangleSurfaceTypes.get(i));
            }
        }

        if (kept.size() < triangles.size()) {
            triangles.clear();
            triangles.addAll(kept);
            featureIds.clear();
            featureIds.addAll(keptIds);
            triangleTextureIds.clear();
            triangleTextureIds.addAll(keptTexIds);
            triangleColored.clear();
            triangleColored.or(keptColored);
            triangleSurfaceTypes.clear();
            triangleSurfaceTypes.addAll(keptSurfaceTypes);
        }
    }

    /**
     * Hash a vertex position to a 64-bit value using exact double bits.
     * Bit-identical positions always hash equal; collisions are possible in
     * principle but statistically irrelevant for realistic coordinate data
     * (birthday-paradox threshold ~4 * 10^9 distinct vertices).
     */
    private static long vertexHash(double[] pos) {
        long h = Double.doubleToLongBits(pos[0]);
        h = h * 31L + Double.doubleToLongBits(pos[1]);
        h = h * 31L + Double.doubleToLongBits(pos[2]);
        return h;
    }

    public double[] computeBoundingBox() {
        if (positions.isEmpty()) {
            return new double[]{0, 0, 0, 0, 0, 0};
        }

        double[] acc = BoundingBoxUtils.emptyAabb();
        for (double[] pos : positions) {
            BoundingBoxUtils.expandToPoint(acc, pos[0], pos[1], pos[2]);
        }
        return acc;
    }

    public double[] computeCenter() {
        double[] bbox = computeBoundingBox();
        return new double[]{
                (bbox[0] + bbox[3]) / 2,
                (bbox[1] + bbox[4]) / 2,
                (bbox[2] + bbox[5]) / 2
        };
    }

    /**
     * Shift all vertex Z values so the mesh's bottom sits at height 0.
     */
    public void clampToGround() {
        if (positions.isEmpty()) {
            return;
        }
        double minZ = Double.MAX_VALUE;
        for (double[] pos : positions) {
            if (pos[2] < minZ) minZ = pos[2];
        }
        if (minZ != 0) {
            for (double[] pos : positions) {
                pos[2] -= minZ;
            }
        }
    }

    /**
     * Compute per-texture UV extent from triangle texture IDs and vertex UVs.
     * Returns texId → [minU, minV, maxU, maxV]. Triangles with texId &lt; 0
     * are skipped.
     */
    public Map<Integer, float[]> computeUVExtents() {
        Map<Integer, float[]> extents = new HashMap<>();
        for (int t = 0; t < triangles.size(); t++) {
            int texId = triangleTextureIds.get(t);
            if (texId < 0) {
                continue;
            }
            float[] ext = extents.computeIfAbsent(texId,
                    k -> new float[]{Float.MAX_VALUE, Float.MAX_VALUE,
                            -Float.MAX_VALUE, -Float.MAX_VALUE});
            int[] tri = triangles.get(t);
            for (int vi : tri) {
                float[] uv = texCoords.get(vi);
                ext[0] = Math.min(ext[0], uv[0]);
                ext[1] = Math.min(ext[1], uv[1]);
                ext[2] = Math.max(ext[2], uv[0]);
                ext[3] = Math.max(ext[3], uv[1]);
            }
        }
        return extents;
    }
}
