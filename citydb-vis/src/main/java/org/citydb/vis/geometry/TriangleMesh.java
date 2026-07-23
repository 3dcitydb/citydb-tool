/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.geometry;

import org.citydb.model.common.Name;
import org.citydb.vis.util.BoundingBoxUtils;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class TriangleMesh {
    private static final float[] WHITE_RGBA = {1f, 1f, 1f, 1f};

    // Bits of the per-triangle outline-edge mask (see TriangleData#outlineEdges):
    // one bit per directed triangle edge in emission order. A set bit marks the
    // edge as part of the source polygon's ring boundary (outer or interior
    // ring), i.e. an edge the CESIUM_primitive_outline extension should draw.
    // Triangulation diagonals, keyhole-bridge edges and T-junction split edges
    // stay unset.
    public static final byte OUTLINE_EDGE_01 = 1;   // v0 -> v1
    public static final byte OUTLINE_EDGE_12 = 2;   // v1 -> v2
    public static final byte OUTLINE_EDGE_20 = 4;   // v2 -> v0
    public static final byte OUTLINE_ALL_EDGES =
            OUTLINE_EDGE_01 | OUTLINE_EDGE_12 | OUTLINE_EDGE_20;

    private final List<double[]> positions;
    private final List<float[]> normals;
    private final List<float[]> texCoords;
    private final List<float[]> colors;
    // Per-triangle attributes (vertex-index triple, featureId, atlas texture
    // id, X3DMaterial-colored flag, source surface type, outline-edge mask),
    // kept index-aligned with one another. The alignment invariant lives
    // entirely inside TriangleData so the add/copy/rebuild paths in merge,
    // removeDuplicate and the T-junction pass ({@link TJunctionResolver})
    // touch one object instead of six parallel collections.
    private TriangleData triangleData;
    private boolean hasTexCoords;
    private boolean hasColors;

    public TriangleMesh() {
        positions = new ArrayList<>();
        normals = new ArrayList<>();
        texCoords = new ArrayList<>();
        colors = new ArrayList<>();
        triangleData = new TriangleData();
    }

    /**
     * Unmodifiable <b>live</b> view of the position lane: vertices appended
     * after the call are visible through it. All vertex-lane getters
     * ({@link #getNormals()}, {@link #getTexCoords()}, {@link #getColors()})
     * share this contract; {@link TJunctionResolver} caches these views
     * across its pass and relies on the liveness — do not harden them into
     * defensive copies.
     */
    public List<double[]> getPositions() {
        return Collections.unmodifiableList(positions);
    }

    /** Unmodifiable live view of the normal lane — same contract as {@link #getPositions()}. */
    public List<float[]> getNormals() {
        return Collections.unmodifiableList(normals);
    }

    /**
     * Unmodifiable view of the <i>current</i> triangle vertex lane. Unlike
     * the vertex-lane views ({@link #getPositions()}), this view detaches
     * when the triangle lanes are rebuilt ({@link #replaceTriangleData},
     * {@link #removeDuplicateTriangles}): it keeps serving the replaced
     * lanes. Re-fetch after any rebuild.
     */
    public List<int[]> getTriangles() {
        return Collections.unmodifiableList(triangleData.vertices);
    }

    /** Unmodifiable live view of the UV lane — same contract as {@link #getPositions()}. */
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

    /** Unmodifiable live view of the color lane — same contract as {@link #getPositions()}. */
    public List<float[]> getColors() {
        return Collections.unmodifiableList(colors);
    }

    public boolean hasColors() {
        return hasColors;
    }

    public List<Long> getFeatureIds() {
        return Collections.unmodifiableList(triangleData.featureIds);
    }

    public int getVertexCount() {
        return positions.size();
    }

    public int getTriangleCount() {
        return triangleData.size();
    }

    public boolean isEmpty() {
        return triangleData.isEmpty();
    }

    public int addVertex(double x, double y, double z, float nx, float ny, float nz) {
        return addVertexInternal(x, y, z, nx, ny, nz, null, null);
    }

    public int addVertex(double x, double y, double z, float nx, float ny, float nz,
                         float u, float v) {
        return addVertexInternal(x, y, z, nx, ny, nz, new float[]{u, v}, null);
    }

    public int addVertex(double x, double y, double z, float nx, float ny, float nz,
                         float r, float g, float b, float a) {
        return addVertexInternal(x, y, z, nx, ny, nz, null, new float[]{r, g, b, a});
    }

    public int addVertex(double x, double y, double z, float nx, float ny, float nz,
                         float u, float v, float r, float g, float b, float a) {
        return addVertexInternal(x, y, z, nx, ny, nz, new float[]{u, v},
                new float[]{r, g, b, a});
    }

    /**
     * Shared vertex insertion maintaining the invariant that whenever
     * {@code hasTexCoords}/{@code hasColors} is true, the respective list is
     * exactly {@code positions.size()} long: the first UV/color vertex
     * backfills all earlier vertices with the neutral value, and a vertex
     * without UV/color pads itself when the mesh already carries that lane.
     */
    private int addVertexInternal(double x, double y, double z, float nx, float ny, float nz,
                                  float[] uv, float[] rgba) {
        int index = positions.size();
        positions.add(new double[]{x, y, z});
        normals.add(new float[]{nx, ny, nz});
        if (uv != null) {
            if (!hasTexCoords) {
                while (texCoords.size() < index) {
                    texCoords.add(new float[]{0f, 0f});
                }
                hasTexCoords = true;
            }
            texCoords.add(uv);
        } else if (hasTexCoords) {
            texCoords.add(new float[]{0f, 0f});
        }
        if (rgba != null) {
            if (!hasColors) {
                while (colors.size() < index) {
                    colors.add(WHITE_RGBA.clone());
                }
                hasColors = true;
            }
            colors.add(rgba);
        } else if (hasColors) {
            colors.add(WHITE_RGBA.clone());
        }
        return index;
    }

    /**
     * Append a triangle with an empty outline-edge mask. Prefer the mask-taking
     * overload on paths that know the edges' ring provenance
     * ({@link PolygonTriangulator}); this variant exists for callers without
     * outline knowledge where "no outlined edge" is the correct conservative
     * default.
     */
    public void addTriangle(int v0, int v1, int v2, long featureId, int textureId,
                            boolean colored, Name surfaceType) {
        addTriangle(v0, v1, v2, featureId, textureId, colored, surfaceType, (byte) 0);
    }

    /**
     * Append a triangle with a boundary-edge mask for outline rendering:
     * a bitwise OR of {@link #OUTLINE_EDGE_01}, {@link #OUTLINE_EDGE_12},
     * {@link #OUTLINE_EDGE_20} marking which edges lie on the source polygon's
     * ring boundary.
     */
    public void addTriangle(int v0, int v1, int v2, long featureId, int textureId,
                            boolean colored, Name surfaceType, byte outlineEdges) {
        triangleData.add(new int[]{v0, v1, v2}, featureId, textureId, colored, surfaceType,
                outlineEdges);
    }

    /**
     * Source surface feature type of the triangle at {@code triIndex} — see
     * {@link TriangleData#surfaceTypes} for semantics. Never {@code null} on
     * meshes built through the standard pipeline; tests that synthesize a
     * mesh directly should pass a non-null type to {@link #addTriangle}.
     */
    public Name getTriangleSurfaceType(int triIndex) {
        return triangleData.surfaceType(triIndex);
    }

    public List<Integer> getTriangleTextureIds() {
        return Collections.unmodifiableList(triangleData.textureIds);
    }

    /**
     * Outline-edge mask of the triangle at {@code triIndex} — a bitwise OR of
     * {@link #OUTLINE_EDGE_01}/{@link #OUTLINE_EDGE_12}/{@link #OUTLINE_EDGE_20}
     * marking which edges lie on the source polygon's ring boundary. {@code 0}
     * when no edge is outlined (interior diagonals only, or the triangle came
     * through a path without outline provenance).
     */
    public byte getTriangleOutlineEdges(int triIndex) {
        return triangleData.outlineEdges(triIndex);
    }

    // Package-private write access for OutlineEdgeMerger.
    void setTriangleOutlineEdges(int triIndex, byte outlineEdges) {
        triangleData.setOutlineEdges(triIndex, outlineEdges);
    }

    /**
     * Clear outline-edge bits on edges shared by exactly two coplanar
     * triangles of the same source surface type — the edges that merely
     * subdivide a flat surface (TIN walls, panelized facades) and would
     * otherwise render as a wireframe over it. Coplanar boundaries between
     * different surface types (a door abutting its wall) keep their outline.
     * {@code scaleX}/{@code scaleY} convert X/Y units to meters for the
     * coplanarity test — same semantics as {@link #resolveTJunctions}; on
     * raw degree coordinates every steep surface degenerates to a
     * near-vertical sheet and a gable roof's ridge would merge away.
     * {@code maxAngleDegrees} widens the coplanarity test beyond the strict
     * float-noise floor: 0 merges exactly-coplanar subdivisions only
     * (CAD-authored data), a positive angle also merges surveyed facets
     * whose dihedral noise is continuous. Call after
     * {@link #resolveTJunctions} and {@link #removeDuplicateTriangles};
     * the algorithm lives in {@link OutlineEdgeMerger}.
     */
    public void mergeCoplanarOutlineEdges(double scaleX, double scaleY, double maxAngleDegrees) {
        OutlineEdgeMerger.merge(this, scaleX, scaleY, maxAngleDegrees);
    }

    /**
     * Whether the triangle at the given index came from an X3DMaterial-colored
     * polygon. Always {@code false} when {@link #hasColors()} is {@code false}.
     */
    public boolean isTriangleColored(int triIndex) {
        return triangleData.isColored(triIndex);
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

        // Append other's triangles, shifting vertex indices by offset. Each
        // triangle's attributes (featureId/textureId/colored/surfaceType/
        // outlineEdges) are copied in lockstep by TriangleData, so no
        // per-attribute bookkeeping (including the manual BitSet shift) is
        // needed here.
        for (int i = 0; i < other.triangleData.size(); i++) {
            int[] tri = other.triangleData.vertices(i);
            triangleData.addCopy(
                    new int[]{tri[0] + offset, tri[1] + offset, tri[2] + offset},
                    other.triangleData, i);
        }
    }

    /**
     * Resolve T-junction vertices by splitting affected triangle edges.
     * A T-junction occurs when a vertex lies on an edge of another triangle
     * without being a vertex of that triangle, causing sub-pixel rendering cracks.
     * <p>
     * The algorithm lives in {@link TJunctionResolver}. Above its triangle-count
     * ceiling ({@value TJunctionResolver#MAX_TRIANGLES}) the pass is skipped with a warning:
     * at that scale (deeply nested BIM features merged into a single mesh) the
     * split-application loop becomes a runaway cost, and the resulting topology
     * changes would weld topologically independent components into shared
     * edges — more harmful than the sub-pixel cracks the pass is meant to fix.
     *
     * @param scaleX degrees-to-meters scale for X (longitude)
     * @param scaleY degrees-to-meters scale for Y (latitude)
     * @param toleranceMeters distance threshold in meters; the grid cell size
     *                        is tuned for tolerance values in the few-centimetre
     *                        range
     */
    public void resolveTJunctions(double scaleX, double scaleY, double toleranceMeters) {
        TJunctionResolver.resolve(this, scaleX, scaleY, toleranceMeters);
    }

    /** Live per-triangle attribute lanes — for same-package collaborators. */
    TriangleData triangleData() {
        return triangleData;
    }

    /**
     * Swap in a rebuilt set of triangle lanes. Vertex lanes are untouched;
     * the caller is responsible for only referencing existing vertex indices.
     */
    void replaceTriangleData(TriangleData triangleData) {
        this.triangleData = triangleData;
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
     * <p>
     * First-wins applies to ALL attribute lanes, including the outline-edge
     * mask: when coincident triangles from two polygons carry different
     * boundary-edge masks, the survivor keeps its own. A missed outline edge
     * on such a duplicate is a sub-pixel cosmetic detail, not worth an
     * OR-merge pass here.
     */
    public void removeDuplicateTriangles() {
        if (triangleData.size() <= 1) return;

        Set<TriangleKey> seen = new HashSet<>();
        TriangleData kept = new TriangleData();

        for (int i = 0; i < triangleData.size(); i++) {
            int[] tri = triangleData.vertices(i);
            long h0 = vertexHash(positions.get(tri[0]));
            long h1 = vertexHash(positions.get(tri[1]));
            long h2 = vertexHash(positions.get(tri[2]));

            // Sort ascending so the key is winding-order-independent.
            if (h0 > h1) { long t = h0; h0 = h1; h1 = t; }
            if (h1 > h2) { long t = h1; h1 = h2; h2 = t; }
            if (h0 > h1) { long t = h0; h0 = h1; h1 = t; }

            if (seen.add(new TriangleKey(h0, h1, h2))) {
                kept.addCopy(tri, triangleData, i);
            }
        }

        if (kept.size() < triangleData.size()) {
            triangleData = kept;
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
     * Shift all vertex Z values so the mesh's lowest point sits at
     * {@code groundHeight} (metres above the WGS84 ellipsoid). Pass {@code 0}
     * to place the mesh on the ellipsoid surface; pass a sampled terrain height
     * to place it on terrain.
     */
    public void clampToGround(double groundHeight) {
        if (positions.isEmpty()) {
            return;
        }
        double minZ = Double.MAX_VALUE;
        for (double[] pos : positions) {
            if (pos[2] < minZ) minZ = pos[2];
        }
        double shift = groundHeight - minZ;
        if (shift != 0) {
            for (double[] pos : positions) {
                pos[2] += shift;
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
        for (int t = 0; t < triangleData.size(); t++) {
            int texId = triangleData.textureId(t);
            if (texId < 0) {
                continue;
            }
            float[] ext = extents.computeIfAbsent(texId,
                    k -> new float[]{Float.MAX_VALUE, Float.MAX_VALUE,
                            -Float.MAX_VALUE, -Float.MAX_VALUE});
            int[] tri = triangleData.vertices(t);
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

    /**
     * Struct-of-arrays holder for the per-triangle attribute lanes —
     * vertex-index triple, source featureId, atlas texture id, the
     * X3DMaterial-colored flag, the source surface feature type, and the
     * outline-edge mask. All six lanes are kept index-aligned: every
     * {@link #add}/{@link #addCopy}/{@link #addAll} appends to all of them
     * together, so the alignment invariant that used to be re-implemented in
     * {@link TriangleMesh#merge}, {@link TriangleMesh#removeDuplicateTriangles}
     * and the T-junction pass now lives in exactly one place.
     *
     * <p>Struct-of-arrays (rather than a list of record objects) is
     * deliberate: the colored lane is a {@link BitSet} (one bit per triangle)
     * and rebuilds replace the whole holder, which preserves the memory layout
     * and scan cost the T-junction pass depends on.
     *
     * <p>Package-private so {@link TJunctionResolver} can build and swap in
     * rebuilt lanes via {@link TriangleMesh#replaceTriangleData}.
     */
    static final class TriangleData {
        private final List<int[]> vertices = new ArrayList<>();
        private final List<Long> featureIds = new ArrayList<>();
        private final List<Integer> textureIds = new ArrayList<>();
        // Set when the triangle's source polygon carried an explicit
        // X3DMaterial color (so its vertex COLOR_0 values are authored, not
        // WHITE_RGBA padding from merge/mixed-feature processing). Drives the
        // GLB writer's untextured-plain vs untextured-unlit primitive split so
        // unappeared surfaces in a colored feature still render PBR-shaded.
        private final BitSet colored = new BitSet();
        // Most-specific Feature ancestor of the triangle's polygon (e.g.
        // RoofSurface / WallSurface for a CityGML 3.0 Building, falling back to
        // the top-level Feature when the geometry is not nested in a boundary
        // surface). Drives per-feature-type styling on the 3D Tiles plain path.
        // Always non-null when filled in through the standard pipeline.
        private final List<Name> surfaceTypes = new ArrayList<>();
        // Outline-edge mask, one byte per triangle (bits OUTLINE_EDGE_01/12/20).
        // Marks edges on the source polygon's ring boundary for
        // CESIUM_primitive_outline; 0 for paths without outline provenance.
        private final List<Byte> outlineEdges = new ArrayList<>();

        int size() {
            return vertices.size();
        }

        boolean isEmpty() {
            return vertices.isEmpty();
        }

        int[] vertices(int i) {
            return vertices.get(i);
        }

        long featureId(int i) {
            return featureIds.get(i);
        }

        int textureId(int i) {
            return textureIds.get(i);
        }

        boolean isColored(int i) {
            return colored.get(i);
        }

        Name surfaceType(int i) {
            return surfaceTypes.get(i);
        }

        byte outlineEdges(int i) {
            return outlineEdges.get(i);
        }

        void setOutlineEdges(int i, byte mask) {
            outlineEdges.set(i, mask);
        }

        /** Append one triangle with explicit attributes. */
        void add(int[] tri, long featureId, int textureId, boolean isColored,
                 Name surfaceType, byte outlineEdgeMask) {
            if (isColored) {
                colored.set(vertices.size());
            }
            vertices.add(tri);
            featureIds.add(featureId);
            textureIds.add(textureId);
            surfaceTypes.add(surfaceType);
            outlineEdges.add(outlineEdgeMask);
        }

        /**
         * Append {@code tri} with the attributes of triangle {@code srcIdx}
         * copied from {@code src} (which may be {@code this}). The caller
         * supplies the vertex triple so it can be offset (merge) or reused
         * unchanged (rebuild/dedup).
         */
        void addCopy(int[] tri, TriangleData src, int srcIdx) {
            add(tri, src.featureIds.get(srcIdx), src.textureIds.get(srcIdx),
                    src.colored.get(srcIdx), src.surfaceTypes.get(srcIdx),
                    src.outlineEdges.get(srcIdx));
        }

        /** Append every triangle of {@code other}, attributes included. */
        void addAll(TriangleData other) {
            for (int i = 0; i < other.size(); i++) {
                addCopy(other.vertices.get(i), other, i);
            }
        }
    }
}
