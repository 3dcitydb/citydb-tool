/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.encoder.tiles3d;

import org.citydb.vis.encoder.AttrValueCoercer;
import org.citydb.vis.geometry.TriangleMesh;
import org.citydb.vis.geometry.VertexWelder;
import org.citydb.vis.model.AttrField;
import org.citydb.vis.model.FeatureData;
import org.citydb.vis.scene.BoundingVolume;
import org.citydb.vis.scene.SceneNode;
import org.citydb.vis.styling.DefaultObjectStyle;
import org.citydb.vis.styling.ObjectStyleRegistry;
import org.citydb.vis.util.GeoTransform;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Encodes a scene node's geometry, textures, and per-feature metadata into a
 * glTF 2.0 Binary (GLB) file without external library dependencies.
 * <p>
 * Each GLB contains:
 * <ul>
 *   <li>Mesh geometry: positions (local ENU), normals or UVs, indices</li>
 *   <li>Textures: one JPEG atlas per page embedded as buffer views</li>
 *   <li>{@code EXT_mesh_features}: per-vertex feature ID attribute</li>
 *   <li>{@code EXT_structural_metadata}: per-feature property table</li>
 * </ul>
 * <p>
 * A node emits up to three flavours of primitives, all sharing the same
 * mesh / property table. NORMAL is only emitted on the no-appearance plain
 * path; textured and X3DMaterial-colored paths render unlit:
 * <ul>
 *   <li>Textured primitive (one per atlas page): {@code POSITION +
 *       TEXCOORD_0 + _FEATURE_ID_0}, uses an unlit textured material backed
 *       by that page. NORMAL omitted; {@code KHR_materials_unlit} prevents
 *       CesiumJS from auto-deriving flat normals (which would dim the
 *       texture).</li>
 *   <li>Untextured-plain primitive (one per distinct {@link DefaultObjectStyle}
 *       used by the node): {@code POSITION + NORMAL + _FEATURE_ID_0}, uses
 *       a per-style PBR material that carries the style's
 *       {@code baseColorFactor}. The only shaded path: per-face NORMAL gives
 *       each surface a 3D form via Lambertian shading. Per-feature-type
 *       overrides reach the GLB through this split — e.g. RoofSurface red +
 *       WallSurface white emits two plain primitives in one node.</li>
 *   <li>Untextured-colored primitive (X3DMaterial): {@code POSITION +
 *       COLOR_0 + _FEATURE_ID_0}, uses an unlit material. NORMAL omitted
 *       so the authored thematic / heat-map colors render at full
 *       intensity.</li>
 * </ul>
 * Positions are in a local ENU coordinate frame relative to a dataset center.
 * The root tile's {@code transform} in {@code tileset.json} converts ENU to ECEF.
 */
public class GlbEncoder {
    /**
     * Encode a mesh node into a GLB byte array. Returns {@code null} if the
     * mesh is empty after welding/degenerate filtering.
     *
     * @param node           scene node (mesh will be cleared after encoding)
     * @param atlasBytesList JPEG bytes per atlas page (index matches
     *                       {@code texIdToPage} values), empty if untextured
     * @param texIdToPage    texture id → atlas page index; empty if untextured
     * @param features       per-feature attribute data
     * @param attrFields     finalized attribute field definitions
     * @param datasetCenter  [centerLon, centerLat, centerAlt] of the dataset
     * @param styleRegistry  per-feature-type style registry. Each plain
     *                       triangle is bucketed by the style its source
     *                       surface type (recorded per-triangle on the
     *                       mesh) resolves to, producing one plain
     *                       primitive (and one plain material) per
     *                       distinct style used by the node. Falls back
     *                       to the registry's default style for types
     *                       without an override.
     * @return GLB bytes, or {@code null} if empty
     */
    public byte[] encode(SceneNode node, List<byte[]> atlasBytesList,
                         Map<Integer, Integer> texIdToPage,
                         List<FeatureData> features, List<AttrField> attrFields,
                         double[] datasetCenter,
                         ObjectStyleRegistry styleRegistry) throws IOException {
        TriangleMesh mesh = node.getMesh();
        boolean hasTexture = mesh.hasTexCoords() && !atlasBytesList.isEmpty();
        if (!hasTexture) {
            node.setTextured(false);
        }

        BoundingVolume mbs = node.getBoundingVolume();
        VertexWelder.WeldResult weld = VertexWelder.weldAndFilter(mesh,
                mbs.getCenterX(), mbs.getCenterY(), mbs.getCenterZ());
        node.setOutputVertexCount(weld.vertexCount());
        if (weld.isEmpty()) {
            node.setMesh(null);
            return null;
        }

        DatasetFrame frame = DatasetFrame.from(mbs, datasetCenter);

        // Partition valid triangles by atlas page (textured) plus two
        // untextured buckets — "plain" (renders with PBR shading, default
        // baseColor) and "colored" (X3DMaterial vertex colors, renders
        // unlit). Plain triangles are further sub-bucketed by the resolved
        // DefaultObjectStyle of each triangle's source surface type, so a
        // CityGML 3.0 Building with mixed RoofSurface / WallSurface /
        // GroundSurface boundaries emits one plain primitive (and one
        // material) per distinct style used. Face-range index is tracked
        // per entry so the per-vertex _FEATURE_ID_0 can reference the
        // shared property table regardless of which primitive the triangle
        // lands in — this preserves the original encoder's semantics, where
        // two non-contiguous runs of the same feature become two distinct
        // property table rows.
        int pageCount = atlasBytesList.size();
        List<List<TriEntry>> texturedTrisByPage;
        // LinkedHashMap so emitted plain primitives appear in the order the
        // styles were first encountered — stable across runs for a given
        // input.
        Map<DefaultObjectStyle, List<TriEntry>> untexturedPlainTrisByStyle = new LinkedHashMap<>();
        List<TriEntry> untexturedColoredTris = new ArrayList<>();
        if (hasTexture) {
            texturedTrisByPage = new ArrayList<>(pageCount);
            for (int i = 0; i < pageCount; i++) {
                texturedTrisByPage.add(new ArrayList<>());
            }
            partitionByAtlasPage(mesh, weld, texIdToPage, styleRegistry,
                    texturedTrisByPage, untexturedPlainTrisByStyle, untexturedColoredTris);
        } else {
            texturedTrisByPage = Collections.emptyList();
            collectAllEntries(mesh, weld, styleRegistry,
                    untexturedPlainTrisByStyle, untexturedColoredTris);
        }

        int featureCount = weld.faceRanges().size();

        // One primitive per non-empty atlas page + one plain primitive per
        // distinct DefaultObjectStyle in use + an optional colored primitive.
        // An empty page can arise when every triangle routed to it was dropped
        // by the weld/degenerate filter; its material + texture would still be
        // referenced in the glTF JSON, but no primitive would draw it, so we
        // simply skip it below and the JSON builder sees a compact set of
        // primitives.
        List<PrimitiveArrays> primitives =
                new ArrayList<>(pageCount + untexturedPlainTrisByStyle.size() + 1);
        for (int p = 0; p < pageCount; p++) {
            List<TriEntry> tris = texturedTrisByPage.get(p);
            if (!tris.isEmpty()) {
                primitives.add(buildPrimitiveArrays(mesh, weld, tris, p, null, frame));
            }
        }
        for (Map.Entry<DefaultObjectStyle, List<TriEntry>> e : untexturedPlainTrisByStyle.entrySet()) {
            if (!e.getValue().isEmpty()) {
                primitives.add(buildPrimitiveArrays(mesh, weld, e.getValue(),
                        UNTEXTURED_PLAIN_PAGE, e.getKey(), frame));
            }
        }
        if (!untexturedColoredTris.isEmpty()) {
            primitives.add(buildPrimitiveArrays(mesh, weld, untexturedColoredTris,
                    UNTEXTURED_COLORED_PAGE, null, frame));
        }
        node.setMesh(null); // release source mesh; no longer needed

        BinBufferBuilder bin = new BinBufferBuilder();
        List<PrimitiveBufferIds> primitiveBvs = new ArrayList<>(primitives.size());
        for (PrimitiveArrays p : primitives) {
            primitiveBvs.add(writePrimitiveBuffers(bin, p));
        }
        // Embed each atlas page that is actually referenced by a primitive.
        // Pages whose triangles were all filtered out contribute nothing.
        List<Integer> bvTextures = new ArrayList<>(pageCount);
        boolean[] pageInUse = new boolean[pageCount];
        for (PrimitiveArrays p : primitives) {
            if (p.atlasPage >= 0) pageInUse[p.atlasPage] = true;
        }
        for (int p = 0; p < pageCount; p++) {
            bvTextures.add(pageInUse[p] ? bin.addRawBytes(atlasBytesList.get(p)) : -1);
        }

        List<FeatureData> propFeatures = featureCount < features.size()
                ? FeatureData.reorderByIds(features, weld.rangeFeatureIds())
                : features;
        List<PropertyTableBufferViews> propBvs = new ArrayList<>();
        for (AttrField field : attrFields) {
            propBvs.add(encodePropertyField(bin, field, propFeatures));
        }

        byte[] binData = bin.toByteArray();
        byte[] jsonData = new GltfJsonBuilder()
                .bufferViews(bin.getBufferViews(), binData.length)
                .textures(bvTextures)
                .primitives(toJsonPrimitives(primitives, primitiveBvs))
                .metadata(featureCount, attrFields, propBvs)
                .build();

        return GlbContainer.assemble(jsonData, binData);
    }

    /**
     * Write a primitive's geometry arrays into the BIN buffer and record the
     * resulting buffer view ids. Ordering within the BIN is unobservable to
     * the glTF client — accessors reference buffer views by index.
     */
    private static PrimitiveBufferIds writePrimitiveBuffers(BinBufferBuilder bin, PrimitiveArrays p) {
        int bvPositions = bin.addFloat32Array(p.positions);
        int bvNormals = p.normals != null ? bin.addFloat32Array(p.normals) : -1;
        int bvUvs = p.uvs != null ? bin.addFloat32Array(p.uvs) : -1;
        int bvColors = p.colors != null ? bin.addFloat32Array(p.colors) : -1;
        int bvIndices = bin.addUint32Array(p.indices);
        int bvFeatureIds = bin.addUint32Array(p.featureIds);
        return new PrimitiveBufferIds(bvPositions, bvNormals, bvUvs, bvColors,
                bvIndices, bvFeatureIds);
    }

    private static List<GltfJsonBuilder.Primitive> toJsonPrimitives(
            List<PrimitiveArrays> primitives, List<PrimitiveBufferIds> bvs) {
        List<GltfJsonBuilder.Primitive> out = new ArrayList<>(primitives.size());
        for (int i = 0; i < primitives.size(); i++) {
            PrimitiveArrays p = primitives.get(i);
            PrimitiveBufferIds b = bvs.get(i);
            out.add(new GltfJsonBuilder.Primitive(
                    p.atlasPage,
                    p.vertexCount, p.posMin, p.posMax,
                    b.positions, b.normals, b.uvs, b.colors, b.indices, b.featureIds,
                    p.anyAlphaBelowOne, p.plainStyle));
        }
        return out;
    }

    /**
     * Walk valid triangles in face-range order and bucket them by atlas page
     * (textured), by resolved {@link DefaultObjectStyle} (untextured plain),
     * or into the colored bucket (carries X3DMaterial vertex color). The
     * face-range index is tracked alongside the original triangle index so
     * the per-vertex {@code _FEATURE_ID_0} can reference the shared property
     * table regardless of which primitive the triangle ends up in.
     */
    private static void partitionByAtlasPage(TriangleMesh mesh, VertexWelder.WeldResult weld,
                                             Map<Integer, Integer> texIdToPage,
                                             ObjectStyleRegistry styleRegistry,
                                             List<List<TriEntry>> texturedByPage,
                                             Map<DefaultObjectStyle, List<TriEntry>> untexturedPlainByStyle,
                                             List<TriEntry> untexturedColored) {
        List<Integer> validTriIndices = weld.validTriIndices();
        List<int[]> faceRanges = weld.faceRanges();
        List<Integer> triTexIds = mesh.getTriangleTextureIds();

        int rangeIdx = 0;
        int[] currentRange = faceRanges.get(0);
        for (int i = 0; i < validTriIndices.size(); i++) {
            while (i > currentRange[1]) {
                currentRange = faceRanges.get(++rangeIdx);
            }
            int ti = validTriIndices.get(i);
            TriEntry entry = new TriEntry(ti, rangeIdx);
            int texId = triTexIds.get(ti);
            if (texId >= 0) {
                Integer page = texIdToPage.get(texId);
                // A texId missing from the map means the atlas builder dropped
                // this texture (e.g. corrupt source). Route the triangle to
                // an untextured bucket so the feature still renders.
                if (page != null) {
                    texturedByPage.get(page).add(entry);
                    continue;
                }
            }
            if (mesh.isTriangleColored(ti)) {
                untexturedColored.add(entry);
            } else {
                bucketPlain(mesh, ti, entry, styleRegistry, untexturedPlainByStyle);
            }
        }
    }

    /**
     * Untextured-only node: every valid triangle goes into one of the
     * untextured buckets — colored ones into {@code untexturedColored},
     * plain ones bucketed by their resolved {@link DefaultObjectStyle}.
     */
    private static void collectAllEntries(TriangleMesh mesh, VertexWelder.WeldResult weld,
                                          ObjectStyleRegistry styleRegistry,
                                          Map<DefaultObjectStyle, List<TriEntry>> untexturedPlainByStyle,
                                          List<TriEntry> untexturedColored) {
        List<Integer> validTriIndices = weld.validTriIndices();
        List<int[]> faceRanges = weld.faceRanges();
        int rangeIdx = 0;
        int[] currentRange = faceRanges.get(0);
        for (int i = 0; i < validTriIndices.size(); i++) {
            while (i > currentRange[1]) {
                currentRange = faceRanges.get(++rangeIdx);
            }
            int ti = validTriIndices.get(i);
            TriEntry entry = new TriEntry(ti, rangeIdx);
            if (mesh.isTriangleColored(ti)) {
                untexturedColored.add(entry);
            } else {
                bucketPlain(mesh, ti, entry, styleRegistry, untexturedPlainByStyle);
            }
        }
    }

    /**
     * Resolve the plain triangle's source surface type to a style via the
     * registry (the registry's own cache makes the per-Name lookup cheap),
     * then append the entry to the bucket for that style.
     */
    private static void bucketPlain(TriangleMesh mesh, int ti, TriEntry entry,
                                    ObjectStyleRegistry registry,
                                    Map<DefaultObjectStyle, List<TriEntry>> bucket) {
        DefaultObjectStyle style = registry.resolve(mesh.getTriangleSurfaceType(ti));
        bucket.computeIfAbsent(style, k -> new ArrayList<>()).add(entry);
    }

    /**
     * Build per-primitive welded vertex arrays from the chosen triangle subset.
     * Rewrites welded positions from ENU (meters, East/North/Up) into glTF
     * Y-up (X=East, Y=Up, Z=-North), collects per-axis min/max for the
     * POSITION accessor, and emits {@code TEXCOORD_0} (textured) /
     * {@code COLOR_0} (X3DMaterial) / {@code NORMAL} (plain only)
     * accordingly. {@code atlasPage}: {@code >=0} = textured (atlas page
     * index, unlit); {@link #UNTEXTURED_PLAIN_PAGE} = untextured-no-color
     * (PBR-shaded, NORMAL emitted); {@link #UNTEXTURED_COLORED_PAGE} =
     * untextured-with-X3DMaterial (unlit, COLOR_0 emitted, no NORMAL). The
     * resulting array's {@code anyAlphaBelowOne} flag drives
     * {@code alphaMode=BLEND} downstream.
     */
    private static PrimitiveArrays buildPrimitiveArrays(TriangleMesh mesh,
                                                        VertexWelder.WeldResult weld,
                                                        List<TriEntry> triEntries,
                                                        int atlasPage,
                                                        DefaultObjectStyle plainStyle,
                                                        DatasetFrame frame) {
        boolean textured = atlasPage >= 0;
        boolean emitColors = atlasPage == UNTEXTURED_COLORED_PAGE;
        // NORMAL only on the no-appearance plain path. Textured/colored paths
        // render unlit (see GltfJsonBuilder.writeMaterials), so NORMAL would
        // be unused — and dropping it also avoids the textured-dim symptom
        // observed on the I3S side for the same data.
        boolean emitNormals = !textured && !emitColors;
        int vertexCount = triEntries.size() * 3;
        float[] positions = new float[vertexCount * 3];
        float[] normals = emitNormals ? new float[vertexCount * 3] : null;
        float[] uvs = textured ? new float[vertexCount * 2] : null;
        float[] colors = emitColors ? new float[vertexCount * 4] : null;
        int[] indices = new int[vertexCount]; // triangle soup: 0,1,2,3,...
        int[] featureIds = new int[vertexCount];
        float[] posMin = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE};
        float[] posMax = {-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        boolean anyAlphaBelowOne = false;

        float[][] weldedPositions = weld.weldedPositions();
        List<int[]> triangles = mesh.getTriangles();

        int idx = 0;
        for (TriEntry entry : triEntries) {
            int ti = entry.ti;
            int base = ti * 3;
            int[] tri = triangles.get(ti);
            int fIdx = entry.faceIdx;
            for (int j = 0; j < 3; j++) {
                float[] wp = weldedPositions[base + j];
                int srcIdx = tri[j];
                float east = wp[0] * (float) frame.scaleX + frame.offsetX;
                float north = wp[1] * (float) frame.scaleY + frame.offsetY;
                float up = wp[2] + frame.offsetZ;
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
                    float[] n = mesh.getNormals().get(srcIdx);
                    normals[idx * 3] = n[0];
                    normals[idx * 3 + 1] = n[2];
                    normals[idx * 3 + 2] = -n[1];
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
                    colors[idx * 4] = srgbToLinear(c[0]);
                    colors[idx * 4 + 1] = srgbToLinear(c[1]);
                    colors[idx * 4 + 2] = srgbToLinear(c[2]);
                    colors[idx * 4 + 3] = c[3];
                    if (c[3] < 0.999f) {
                        anyAlphaBelowOne = true;
                    }
                }
                indices[idx] = idx;
                featureIds[idx] = fIdx;
                idx++;
            }
        }

        return new PrimitiveArrays(atlasPage, vertexCount, positions, normals, uvs,
                colors, indices, featureIds, posMin, posMax, anyAlphaBelowOne,
                plainStyle);
    }

    /**
     * Scale/offset from node-local meters to dataset-centered ENU meters.
     * Welded positions are relative to the node center; the offset shifts them
     * into the dataset-centered frame used by the root tile's ENU-to-ECEF transform.
     */
    private record DatasetFrame(double scaleX, double scaleY,
                                float offsetX, float offsetY, float offsetZ) {
        static DatasetFrame from(BoundingVolume mbs, double[] datasetCenter) {
            double scaleX = GeoTransform.metersPerDegreeLon(datasetCenter[1]);
            double scaleY = GeoTransform.WGS84_METERS_PER_DEGREE_LAT;
            float offsetX = (float) ((mbs.getCenterX() - datasetCenter[0]) * scaleX);
            float offsetY = (float) ((mbs.getCenterY() - datasetCenter[1]) * scaleY);
            float offsetZ = (float) (mbs.getCenterZ() - datasetCenter[2]);
            return new DatasetFrame(scaleX, scaleY, offsetX, offsetY, offsetZ);
        }
    }

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
     * iff this sentinel was {@code _COLORED}.
     */
    private static final int UNTEXTURED_PLAIN_PAGE = -1;
    private static final int UNTEXTURED_COLORED_PAGE = -2;

    /**
     * One valid triangle earmarked for a specific primitive, paired with the
     * face-range (property table row) it belongs to.
     */
    private record TriEntry(int ti, int faceIdx) {
    }

    private record PrimitiveArrays(int atlasPage, int vertexCount,
                                   float[] positions, float[] normals, float[] uvs,
                                   float[] colors,
                                   int[] indices, int[] featureIds,
                                   float[] posMin, float[] posMax,
                                   boolean anyAlphaBelowOne,
                                   DefaultObjectStyle plainStyle) {
    }

    private record PrimitiveBufferIds(int positions, int normals, int uvs, int colors,
                                      int indices, int featureIds) {
    }

    /**
     * IEC 61966-2-1 sRGB → linear, applied per RGB channel when baking
     * X3DMaterial colors into glTF {@code COLOR_0}.
     */
    private static float srgbToLinear(float c) {
        if (c <= 0.04045f) {
            return c / 12.92f;
        }
        return (float) Math.pow((c + 0.055f) / 1.055f, 2.4);
    }

    // ---- Property table encoding ----------------------------------------

    private static PropertyTableBufferViews encodePropertyField(
            BinBufferBuilder bin, AttrField field, List<FeatureData> features) {
        String name = field.name();
        return switch (field.type()) {
            case OID, INT -> new PropertyTableBufferViews(
                    bin.addInt32Array(AttrValueCoercer.extractInts(features, name)), -1);
            case DOUBLE -> new PropertyTableBufferViews(
                    bin.addFloat64Array(AttrValueCoercer.extractDoubles(features, name)), -1);
            case STRING -> encodeStringProperty(bin, name, features);
        };
    }

    private static PropertyTableBufferViews encodeStringProperty(
            BinBufferBuilder bin, String fieldName, List<FeatureData> features) {
        byte[][] utf8 = AttrValueCoercer.extractUtf8(features, fieldName);
        ByteArrayOutputStream valuesStream = new ByteArrayOutputStream();
        int[] offsets = new int[utf8.length + 1];
        int offset = 0;
        for (int i = 0; i < utf8.length; i++) {
            offsets[i] = offset;
            valuesStream.writeBytes(utf8[i]);
            offset += utf8[i].length;
        }
        offsets[utf8.length] = offset;

        int valuesBv = bin.addRawBytes(valuesStream.toByteArray());
        int offsetsBv = bin.addUint32Array(offsets);
        return new PropertyTableBufferViews(valuesBv, offsetsBv);
    }
}
