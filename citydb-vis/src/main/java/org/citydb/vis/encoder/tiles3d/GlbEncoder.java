/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.encoder.tiles3d;

import org.citydb.vis.attribute.AttributeValueCoercer;
import org.citydb.vis.encoder.TriangleRouter;
import org.citydb.vis.encoder.TriangleRouter.RoutedTriangle;
import org.citydb.vis.geometry.TriangleMesh;
import org.citydb.vis.geometry.VertexWelder;
import org.citydb.vis.model.AttrField;
import org.citydb.vis.model.FeatureData;
import org.citydb.vis.scene.BoundingVolume;
import org.citydb.vis.scene.SceneNode;
import org.citydb.vis.styling.DefaultObjectStyle;
import org.citydb.vis.styling.ObjectStyleRegistry;
import org.citydb.vis.util.ColorUtils;
import org.citydb.vis.util.GeoTransform;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
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
 * mesh / property table. {@code --enable-shading} toggles NORMAL emission
 * and the {@code KHR_materials_unlit} extension uniformly across every
 * primitive type, mirroring the I3S writer:
 * <ul>
 *   <li>Textured primitive (one per atlas page): {@code POSITION +
 *       TEXCOORD_0 + _FEATURE_ID_0}, plus {@code NORMAL} when
 *       {@code --enable-shading} is on. The emitted normal is the local
 *       "up" direction (+Y in GLB Y-up local frame, which the root tile
 *       transform rotates to ECEF up at the dataset center) — not the
 *       polygon's true geometric normal. Lambertian then evaluates against
 *       a single direction across all textured triangles in the node, so
 *       walls and roofs end up at the same brightness (matches the I3S
 *       up-normal trick). When the flag is off the material carries
 *       {@code KHR_materials_unlit} to prevent CesiumJS from auto-deriving
 *       flat normals (which would dim the texture).</li>
 *   <li>Untextured-plain primitive (one per distinct {@link DefaultObjectStyle}
 *       used by the node): {@code POSITION + _FEATURE_ID_0}, plus
 *       {@code NORMAL} when {@code --enable-shading} is on. The material
 *       carries the style's {@code baseColorFactor} and switches between
 *       PBR-shaded and {@code KHR_materials_unlit} based on the flag.
 *       Per-feature-type overrides reach the GLB through this split —
 *       e.g. RoofSurface red + WallSurface white emits two plain primitives
 *       in one node.</li>
 *   <li>Untextured-colored primitive (X3DMaterial): {@code POSITION +
 *       COLOR_0 + _FEATURE_ID_0}, plus {@code NORMAL} when
 *       {@code --enable-shading} is on. Its material is PBR when shaded
 *       and unlit otherwise.</li>
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
                         ObjectStyleRegistry styleRegistry,
                         boolean enableShading) throws IOException {
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

        // Classify valid triangles into format-neutral routing facts once
        // (texture id, X3DMaterial-colored flag, resolved style, face-range
        // row), then bucket them into the GLB's primitive flavours: one
        // textured primitive per atlas page, one "plain" primitive per
        // distinct DefaultObjectStyle (PBR-shaded, default baseColor), and one
        // "colored" primitive (X3DMaterial vertex colors, unlit). A CityGML
        // 3.0 Building with mixed RoofSurface / WallSurface / GroundSurface
        // boundaries therefore emits one plain primitive (and one material)
        // per distinct style used. The face-range index carried on each routed
        // triangle lets the per-vertex _FEATURE_ID_0 reference the shared
        // property table regardless of which primitive the triangle lands in —
        // two non-contiguous runs of the same feature become two distinct
        // property table rows.
        List<RoutedTriangle> routed = TriangleRouter.route(mesh, weld, styleRegistry);

        int pageCount = atlasBytesList.size();
        List<List<RoutedTriangle>> texturedTrisByPage = new ArrayList<>(pageCount);
        for (int i = 0; i < pageCount; i++) {
            texturedTrisByPage.add(new ArrayList<>());
        }
        // LinkedHashMap so emitted plain primitives appear in the order the
        // styles were first encountered — stable across runs for a given
        // input.
        Map<DefaultObjectStyle, List<RoutedTriangle>> untexturedPlainTrisByStyle = new LinkedHashMap<>();
        List<RoutedTriangle> untexturedColoredTris = new ArrayList<>();
        for (RoutedTriangle rt : routed) {
            if (hasTexture && rt.textured()) {
                Integer page = texIdToPage.get(rt.textureId());
                // A texId missing from the map means the atlas builder dropped
                // this texture (e.g. corrupt source). Route the triangle to an
                // untextured bucket so the feature still renders.
                if (page != null) {
                    texturedTrisByPage.get(page).add(rt);
                    continue;
                }
            }
            if (rt.colored()) {
                untexturedColoredTris.add(rt);
            } else {
                untexturedPlainTrisByStyle.computeIfAbsent(rt.style(), k -> new ArrayList<>()).add(rt);
            }
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
            List<RoutedTriangle> tris = texturedTrisByPage.get(p);
            if (!tris.isEmpty()) {
                primitives.add(buildPrimitiveArrays(mesh, weld, tris, p, null, frame, enableShading));
            }
        }
        for (Map.Entry<DefaultObjectStyle, List<RoutedTriangle>> e : untexturedPlainTrisByStyle.entrySet()) {
            if (!e.getValue().isEmpty()) {
                primitives.add(buildPrimitiveArrays(mesh, weld, e.getValue(),
                        UNTEXTURED_PLAIN_PAGE, e.getKey(), frame, enableShading));
            }
        }
        if (!untexturedColoredTris.isEmpty()) {
            primitives.add(buildPrimitiveArrays(mesh, weld, untexturedColoredTris,
                    UNTEXTURED_COLORED_PAGE, null, frame, enableShading));
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
                .enableShading(enableShading)
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
        // glTF core forbids UNSIGNED_INT on vertex attributes (only allowed on
        // indices). EXT_mesh_features's _FEATURE_ID_n is a vertex attribute,
        // so we widen to FLOAT — supports up to 2^24 unique IDs per node, more
        // than enough for property-table row indices.
        float[] featureIdsFloat = new float[p.featureIds.length];
        for (int i = 0; i < p.featureIds.length; i++) featureIdsFloat[i] = p.featureIds[i];
        int bvFeatureIds = bin.addFloat32Array(featureIdsFloat);
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
                                                        List<RoutedTriangle> triEntries,
                                                        int atlasPage,
                                                        DefaultObjectStyle plainStyle,
                                                        DatasetFrame frame,
                                                        boolean enableShading) {
        boolean textured = atlasPage >= 0;
        boolean emitColors = atlasPage == UNTEXTURED_COLORED_PAGE;
        // NORMAL emitted on every path when --enable-shading is on. Textured
        // primitives use the up-direction trick (constant +Y in local frame,
        // see normal-write block below) so walls and roofs render at uniform
        // brightness — matches the I3S writer's behaviour.
        boolean emitNormals = enableShading;
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
        for (RoutedTriangle entry : triEntries) {
            int ti = entry.triangleIndex();
            int base = ti * 3;
            int[] tri = triangles.get(ti);
            int fIdx = entry.faceIndex();
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
                    if (textured) {
                        // Up-direction trick: emit local +Y (which the root
                        // tile transform rotates to ECEF up at the dataset
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

    // ---- Property table encoding ----------------------------------------

    private static PropertyTableBufferViews encodePropertyField(
            BinBufferBuilder bin, AttrField field, List<FeatureData> features) {
        return AttributeValueCoercer.dispatchByType(field.type(), features, field.name(),
                v -> new PropertyTableBufferViews(bin.addInt32Array(v), -1),
                v -> new PropertyTableBufferViews(bin.addFloat64Array(v), -1),
                v -> encodeStringProperty(bin, v));
    }

    private static PropertyTableBufferViews encodeStringProperty(
            BinBufferBuilder bin, byte[][] utf8) {
        ByteArrayOutputStream valuesStream = new ByteArrayOutputStream();
        int[] offsets = new int[utf8.length + 1];
        int offset = 0;
        for (int i = 0; i < utf8.length; i++) {
            offsets[i] = offset;
            valuesStream.writeBytes(utf8[i]);
            offset += utf8[i].length;
        }
        offsets[utf8.length] = offset;

        // glTF rejects bufferView.byteLength == 0, while EXT_structural_metadata
        // requires the values bufferView byteLength to equal the last string
        // offset. When every value is empty there's no way to satisfy both —
        // skip the property entirely. The schema keeps the column (other GLBs
        // in the same tileset may populate it); 3D Tiles 1.1 lets a property
        // table omit non-required properties.
        if (valuesStream.size() == 0) {
            return PropertyTableBufferViews.SKIPPED;
        }
        int valuesBv = bin.addRawBytes(valuesStream.toByteArray());
        int offsetsBv = bin.addInt32Array(offsets);
        return new PropertyTableBufferViews(valuesBv, offsetsBv);
    }
}
