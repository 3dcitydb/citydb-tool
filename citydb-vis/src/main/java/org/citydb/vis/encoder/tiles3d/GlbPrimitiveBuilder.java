/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.encoder.tiles3d;

import org.citydb.vis.encoder.TriangleRouter.RoutedTriangle;
import org.citydb.vis.geometry.TriangleMesh;
import org.citydb.vis.geometry.VertexWelder;
import org.citydb.vis.styling.DefaultObjectStyle;
import org.citydb.vis.util.ColorUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Builds one GLB primitive from a routed-triangle subset: welded vertex
 * arrays in glTF Y-up ({@link #build}), their BIN buffer views
 * ({@link #writeBuffers}), and the {@link GltfJsonBuilder.Primitive} binding
 * ({@link #toJsonPrimitive}). Shared by the main-mesh path in
 * {@link GlbEncoder} and the GPU-instancing path in
 * {@link InstancedNodeEncoder}, which differ only in how they bucket
 * triangles and bind materials.
 */
final class GlbPrimitiveBuilder {
    /**
     * {@link PrimitiveArrays#atlasPage} sentinels for the two untextured
     * primitive flavours. {@code _PLAIN} means "no X3DMaterial color" — the
     * primitive carries a {@link DefaultObjectStyle} on
     * {@link PrimitiveArrays#plainStyle} and gets a per-style PBR material
     * with {@code NORMAL}-driven Lambertian shading (the only shaded path).
     * {@code _COLORED} means "has X3DMaterial vertex color" — emits
     * {@code COLOR_0} with an unlit material so authored thematic colors
     * render at full intensity. The {@link GltfJsonBuilder} distinguishes
     * the two via the primitive's {@code bvColors >= 0} flag, which is set
     * iff this sentinel was {@code _COLORED}. The instanced-textured sentinel
     * is owned by {@link GltfJsonBuilder#INSTANCED_TEXTURED_PAGE} (which does
     * the material routing on it); this class references it directly.
     */
    static final int UNTEXTURED_PLAIN_PAGE = -1;
    static final int UNTEXTURED_COLORED_PAGE = -2;

    /**
     * Per-primitive welded vertex arrays plus the accessor facts derived
     * while building them (bounds, unique-feature count, alpha flag).
     * {@code outlineIndices} is the CESIUM_primitive_outline edge list —
     * pairs of indices into this primitive's vertex arrays, one pair per
     * boundary edge — or {@code null} when outline emission is off or no
     * triangle of the subset carries a boundary edge.
     */
    record PrimitiveArrays(int atlasPage, int vertexCount,
                           float[] positions, float[] normals, float[] uvs,
                           float[] colors,
                           int[] indices, int[] featureIds,
                           int[] outlineIndices,
                           int uniqueFeatureCount,
                           float[] posMin, float[] posMax,
                           boolean anyAlphaBelowOne,
                           DefaultObjectStyle plainStyle) {
    }

    /** BIN buffer view ids of one primitive's arrays ({@code -1} = absent). */
    record PrimitiveBufferIds(int positions, int normals, int uvs, int colors,
                              int indices, int featureIds, int outlines) {
    }

    private GlbPrimitiveBuilder() {
    }

    /**
     * Build per-primitive welded vertex arrays from the chosen triangle subset.
     * Rewrites welded positions from ENU (meters, East/North/Up) into glTF
     * Y-up (X=East, Y=Up, Z=-North), collects per-axis min/max for the
     * POSITION accessor, and emits {@code TEXCOORD_0} (textured) /
     * {@code COLOR_0} (X3DMaterial) accordingly; {@code NORMAL} is emitted on
     * every path when {@code --enable-shading} is on (textured paths carry
     * the up-direction trick, see the in-body comment) and on none otherwise.
     * {@code atlasPage}: {@code >=0} = textured against a node atlas page;
     * {@link GltfJsonBuilder#INSTANCED_TEXTURED_PAGE} = textured against the
     * instanced prototype's own atlas; {@link #UNTEXTURED_PLAIN_PAGE} =
     * untextured-no-color (per-style material);
     * {@link #UNTEXTURED_COLORED_PAGE} = untextured-with-X3DMaterial
     * ({@code COLOR_0} emitted). The resulting array's
     * {@code anyAlphaBelowOne} flag drives {@code alphaMode=BLEND}
     * downstream.
     * <p>
     * {@code emitOutlines} additionally collects the CESIUM_primitive_outline
     * edge list from the mesh's per-triangle outline masks. The triangle-soup
     * layout makes this a pure index computation: output triangle {@code k}
     * occupies vertices {@code 3k..3k+2}, so a masked edge {@code e} of that
     * triangle contributes the pair {@code (3k+e, 3k+(e+1)%3)}.
     */
    static PrimitiveArrays build(TriangleMesh mesh,
                                 VertexWelder.WeldResult weld,
                                 List<RoutedTriangle> triEntries,
                                 int atlasPage,
                                 DefaultObjectStyle plainStyle,
                                 CellFrame frame,
                                 boolean enableShading,
                                 boolean perVertexFeatureIds,
                                 boolean emitOutlines) {
        boolean textured = atlasPage >= 0 || atlasPage == GltfJsonBuilder.INSTANCED_TEXTURED_PAGE;
        boolean emitColors = atlasPage == UNTEXTURED_COLORED_PAGE;
        // NORMAL emitted on every path when --enable-shading is on. Textured
        // primitives use the up-direction trick (constant +Y in local frame,
        // see normal-write block below) so walls and roofs render at uniform
        // brightness — matches the I3S writer's behaviour. Known limitation
        // on the instanced path: the constant +Y is in PROTOTYPE-LOCAL space
        // and the GPU rotates it by each instance's ROTATION attribute, so
        // pitched/rolled instances shade as if lit from their local up. Exact
        // for the dominant upright (yaw-only) case; a per-instance world-up
        // is not expressible with shared per-vertex normals.
        boolean emitNormals = enableShading;
        int vertexCount = triEntries.size() * 3;
        float[] positions = new float[vertexCount * 3];
        float[] normals = emitNormals ? new float[vertexCount * 3] : null;
        float[] uvs = textured ? new float[vertexCount * 2] : null;
        float[] colors = emitColors ? new float[vertexCount * 4] : null;
        int[] indices = new int[vertexCount]; // triangle soup: 0,1,2,3,...
        int[] featureIds = perVertexFeatureIds ? new int[vertexCount] : null;
        float[] posMin = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE};
        float[] posMax = {-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        boolean anyAlphaBelowOne = false;

        float[][] weldedPositions = weld.weldedPositions();
        List<int[]> triangles = mesh.getTriangles();

        // Distinct property-table rows referenced by THIS primitive —
        // EXT_mesh_features defines featureCount per feature-id attribute,
        // not per node, and after the per-page/per-style split each
        // primitive sees only a subset of the node's rows.
        Set<Integer> uniqueFeatures = perVertexFeatureIds ? new HashSet<>() : null;

        int idx = 0;
        for (RoutedTriangle entry : triEntries) {
            int ti = entry.triangleIndex();
            int base = ti * 3;
            int[] tri = triangles.get(ti);
            int fIdx = entry.faceIndex();
            if (uniqueFeatures != null) {
                uniqueFeatures.add(fIdx);
            }
            for (int j = 0; j < 3; j++) {
                float[] wp = weldedPositions[base + j];
                int srcIdx = tri[j];
                float east = wp[0] * (float) frame.scaleX() + frame.offsetX();
                float north = wp[1] * (float) frame.scaleY() + frame.offsetY();
                float up = wp[2] + frame.offsetZ();
                positions[idx * 3] = east;
                positions[idx * 3 + 1] = up;
                positions[idx * 3 + 2] = -north;
                posMin[0] = Math.min(posMin[0], east);
                posMin[1] = Math.min(posMin[1], up);
                posMin[2] = Math.min(posMin[2], -north);
                posMax[0] = Math.max(posMax[0], east);
                posMax[1] = Math.max(posMax[1], up);
                posMax[2] = Math.max(posMax[2], -north);

                if (normals != null) {
                    if (textured) {
                        // Up-direction trick: emit local +Y (which the per-cell
                        // tile transform rotates to ECEF up at the cell
                        // center) for every textured vertex, instead of the
                        // polygon's geometric normal. Lambertian then yields
                        // the same brightness for every textured triangle in
                        // the node — walls and roofs equally lit, no per-face
                        // dimming on back-facing walls. Mirrors the I3S
                        // writer (see I3SGeometryEncoder.fillUpInEcef).
                        normals[idx * 3] = 0f;
                        normals[idx * 3 + 1] = 1f;
                        normals[idx * 3 + 2] = 0f;
                    } else {
                        float[] n = mesh.getNormals().get(srcIdx);
                        normals[idx * 3] = n[0];
                        normals[idx * 3 + 1] = n[2];
                        normals[idx * 3 + 2] = -n[1];
                    }
                }
                if (uvs != null) {
                    float[] uv = mesh.getTexCoords().get(srcIdx);
                    uvs[idx * 2] = uv[0];
                    uvs[idx * 2 + 1] = uv[1];
                }
                if (colors != null) {
                    // Mesh colors are sRGB display values (as authored in
                    // X3DMaterial); glTF mandates linear COLOR_0, so convert
                    // RGB on the way out. Alpha is not a color channel and
                    // passes through unchanged. The I3S writer keeps raw
                    // sRGB values per Cesium's I3S loader convention; see
                    // RingAppearance javadoc for rationale.
                    float[] c = mesh.getColors().get(srcIdx);
                    colors[idx * 4] = ColorUtils.srgbToLinear(c[0]);
                    colors[idx * 4 + 1] = ColorUtils.srgbToLinear(c[1]);
                    colors[idx * 4 + 2] = ColorUtils.srgbToLinear(c[2]);
                    colors[idx * 4 + 3] = c[3];
                    if (c[3] < 0.999f) {
                        anyAlphaBelowOne = true;
                    }
                }
                indices[idx] = idx;
                if (featureIds != null) {
                    featureIds[idx] = fIdx;
                }
                idx++;
            }
        }

        int[] outlineIndices = emitOutlines ? buildOutlineIndices(mesh, triEntries) : null;

        return new PrimitiveArrays(atlasPage, vertexCount, positions, normals, uvs,
                colors, indices, featureIds, outlineIndices,
                uniqueFeatures != null ? uniqueFeatures.size() : 0,
                posMin, posMax, anyAlphaBelowOne, plainStyle);
    }

    /**
     * Collect the outlined edges of the triangle subset as index pairs into
     * the primitive's own (soup-ordered) vertex arrays. Returns {@code null}
     * when no triangle carries a boundary edge, so no empty accessor is
     * emitted downstream.
     */
    private static int[] buildOutlineIndices(TriangleMesh mesh, List<RoutedTriangle> triEntries) {
        int count = 0;
        for (RoutedTriangle entry : triEntries) {
            count += 2 * Integer.bitCount(mesh.getTriangleOutlineEdges(entry.triangleIndex()) & 0x7);
        }
        if (count == 0) {
            return null;
        }

        int[] outlineIndices = new int[count];
        int oi = 0;
        for (int k = 0; k < triEntries.size(); k++) {
            byte mask = mesh.getTriangleOutlineEdges(triEntries.get(k).triangleIndex());
            int base = k * 3;
            for (int e = 0; e < 3; e++) {
                if ((mask & (1 << e)) != 0) {
                    outlineIndices[oi++] = base + e;
                    outlineIndices[oi++] = base + (e + 1) % 3;
                }
            }
        }
        return outlineIndices;
    }

    /**
     * Write a primitive's geometry arrays into the BIN buffer and record the
     * resulting buffer view ids. Ordering within the BIN is unobservable to
     * the glTF client — accessors reference buffer views by index.
     */
    static PrimitiveBufferIds writeBuffers(BinBufferBuilder bin, PrimitiveArrays p) {
        int bvPositions = bin.addFloat32Array(p.positions());
        int bvNormals = p.normals() != null ? bin.addFloat32Array(p.normals()) : -1;
        int bvUvs = p.uvs() != null ? bin.addFloat32Array(p.uvs()) : -1;
        int bvColors = p.colors() != null ? bin.addFloat32Array(p.colors()) : -1;
        int bvIndices = bin.addUint32Array(p.indices());
        // glTF core forbids UNSIGNED_INT on vertex attributes (only allowed on
        // indices). EXT_mesh_features's _FEATURE_ID_n is a vertex attribute,
        // so we widen to FLOAT — supports up to 2^24 unique IDs per node, more
        // than enough for property-table row indices. Instanced prototype
        // primitives carry no per-vertex ids (featureIds == null) — their
        // feature identity is per instance via EXT_instance_features.
        int bvFeatureIds = -1;
        if (p.featureIds() != null) {
            float[] featureIdsFloat = new float[p.featureIds().length];
            for (int i = 0; i < p.featureIds().length; i++) featureIdsFloat[i] = p.featureIds()[i];
            bvFeatureIds = bin.addFloat32Array(featureIdsFloat);
        }
        int bvOutlines = p.outlineIndices() != null ? bin.addUint32Array(p.outlineIndices()) : -1;
        return new PrimitiveBufferIds(bvPositions, bvNormals, bvUvs, bvColors,
                bvIndices, bvFeatureIds, bvOutlines);
    }

    /**
     * Bind one primitive's arrays and buffer views into the JSON-builder
     * shape. {@code styleOverride} lets instanced style groups share one
     * style-independent plain geometry set while binding their own material;
     * {@code null} keeps the style baked into the arrays (main mesh path).
     * {@code bvInstancedAtlas} is the atlas-page bufferView an instanced
     * textured primitive samples; {@code -1} on every other flavour
     * (including all main-mesh primitives, whose textured material is routed
     * by {@code atlasPage} instead).
     */
    static GltfJsonBuilder.Primitive toJsonPrimitive(
            PrimitiveArrays p, PrimitiveBufferIds b, DefaultObjectStyle styleOverride,
            int bvInstancedAtlas) {
        return new GltfJsonBuilder.Primitive(
                p.atlasPage(),
                p.vertexCount(), p.uniqueFeatureCount(), p.posMin(), p.posMax(),
                b.positions(), b.normals(), b.uvs(), b.colors(), b.indices(), b.featureIds(),
                bvInstancedAtlas,
                b.outlines(), p.outlineIndices() != null ? p.outlineIndices().length : 0,
                p.anyAlphaBelowOne(), styleOverride != null ? styleOverride : p.plainStyle());
    }
}
