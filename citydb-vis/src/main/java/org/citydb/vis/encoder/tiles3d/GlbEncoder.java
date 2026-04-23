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
import org.citydb.vis.util.GeoTransform;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
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
 * A node emits one textured primitive per atlas page plus an optional
 * untextured primitive, all sharing the same mesh / property table:
 * <ul>
 *   <li>Textured primitive (per atlas page): {@code POSITION + TEXCOORD_0 +
 *       _FEATURE_ID_0}, uses a textured PBR material backed by that page.
 *       Normals are omitted so CesiumJS generates flat per-face normals at
 *       render time — matches the I3S behaviour and looks best on faceted
 *       building geometry.</li>
 *   <li>Untextured primitive: {@code POSITION + NORMAL + _FEATURE_ID_0}, uses
 *       a default PBR material. Encoded per-face normals give proper lighting
 *       with the plain white base color.</li>
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
     * @return GLB bytes, or {@code null} if empty
     */
    public byte[] encode(SceneNode node, List<byte[]> atlasBytesList,
                         Map<Integer, Integer> texIdToPage,
                         List<FeatureData> features, List<AttrField> attrFields,
                         double[] datasetCenter) throws IOException {
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

        // Partition valid triangles by atlas page (textured) plus a single
        // untextured bucket. Face-range index is tracked per entry so the
        // per-vertex _FEATURE_ID_0 can reference the shared property table
        // regardless of which primitive the triangle lands in — this
        // preserves the original encoder's semantics, where two
        // non-contiguous runs of the same feature become two distinct
        // property table rows.
        int pageCount = atlasBytesList.size();
        List<List<TriEntry>> texturedTrisByPage;
        List<TriEntry> untexturedTris;
        if (hasTexture) {
            texturedTrisByPage = new ArrayList<>(pageCount);
            for (int i = 0; i < pageCount; i++) {
                texturedTrisByPage.add(new ArrayList<>());
            }
            untexturedTris = new ArrayList<>();
            partitionByAtlasPage(mesh, weld, texIdToPage, texturedTrisByPage, untexturedTris);
        } else {
            texturedTrisByPage = Collections.emptyList();
            untexturedTris = collectAllEntries(weld);
        }

        int featureCount = weld.faceRanges().size();

        // One primitive per non-empty atlas page + optional untextured
        // primitive. An empty page can arise when every triangle routed to it
        // was dropped by the weld/degenerate filter; its material + texture
        // would still be referenced in the glTF JSON, but no primitive would
        // draw it, so we simply skip it below and the JSON builder sees a
        // compact set of primitives.
        List<PrimitiveArrays> primitives = new ArrayList<>(pageCount + 1);
        for (int p = 0; p < pageCount; p++) {
            List<TriEntry> tris = texturedTrisByPage.get(p);
            if (!tris.isEmpty()) {
                primitives.add(buildPrimitiveArrays(mesh, weld, tris, p, frame));
            }
        }
        if (!untexturedTris.isEmpty()) {
            primitives.add(buildPrimitiveArrays(mesh, weld, untexturedTris,
                    UNTEXTURED_PAGE, frame));
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
        int bvIndices = bin.addUint32Array(p.indices);
        int bvFeatureIds = bin.addUint32Array(p.featureIds);
        return new PrimitiveBufferIds(bvPositions, bvNormals, bvUvs, bvIndices, bvFeatureIds);
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
                    b.positions, b.normals, b.uvs, b.indices, b.featureIds));
        }
        return out;
    }

    /**
     * Walk valid triangles in face-range order and bucket them by atlas page
     * (for textured triangles) or into the untextured list. The face-range
     * index is tracked alongside the original triangle index so the per-vertex
     * {@code _FEATURE_ID_0} can reference the shared property table regardless
     * of which primitive the triangle ends up in.
     */
    private static void partitionByAtlasPage(TriangleMesh mesh, VertexWelder.WeldResult weld,
                                             Map<Integer, Integer> texIdToPage,
                                             List<List<TriEntry>> texturedByPage,
                                             List<TriEntry> untextured) {
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
                // the untextured bucket so the feature still renders.
                if (page != null) {
                    texturedByPage.get(page).add(entry);
                } else {
                    untextured.add(entry);
                }
            } else {
                untextured.add(entry);
            }
        }
    }

    /** Untextured-only node: every valid triangle goes straight to the untextured primitive. */
    private static List<TriEntry> collectAllEntries(VertexWelder.WeldResult weld) {
        List<Integer> validTriIndices = weld.validTriIndices();
        List<int[]> faceRanges = weld.faceRanges();
        List<TriEntry> entries = new ArrayList<>(validTriIndices.size());
        int rangeIdx = 0;
        int[] currentRange = faceRanges.get(0);
        for (int i = 0; i < validTriIndices.size(); i++) {
            while (i > currentRange[1]) {
                currentRange = faceRanges.get(++rangeIdx);
            }
            entries.add(new TriEntry(validTriIndices.get(i), rangeIdx));
        }
        return entries;
    }

    /**
     * Build per-primitive welded vertex arrays from the chosen triangle subset.
     * Rewrites welded positions from ENU (meters, East/North/Up) into glTF
     * Y-up (X=East, Y=Up, Z=-North), collects per-axis min/max for the
     * POSITION accessor, and emits {@code NORMAL} (when untextured) or
     * {@code TEXCOORD_0} (when textured) accordingly. {@code atlasPage >= 0}
     * selects the textured path; {@link #UNTEXTURED_PAGE} selects untextured.
     */
    private static PrimitiveArrays buildPrimitiveArrays(TriangleMesh mesh,
                                                        VertexWelder.WeldResult weld,
                                                        List<TriEntry> triEntries,
                                                        int atlasPage, DatasetFrame frame) {
        boolean textured = atlasPage >= 0;
        int vertexCount = triEntries.size() * 3;
        float[] positions = new float[vertexCount * 3];
        float[] normals = textured ? null : new float[vertexCount * 3];
        float[] uvs = textured ? new float[vertexCount * 2] : null;
        int[] indices = new int[vertexCount]; // triangle soup: 0,1,2,3,...
        int[] featureIds = new int[vertexCount];
        float[] posMin = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE};
        float[] posMax = {-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};

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
                indices[idx] = idx;
                featureIds[idx] = fIdx;
                idx++;
            }
        }

        return new PrimitiveArrays(atlasPage, vertexCount, positions, normals, uvs,
                indices, featureIds, posMin, posMax);
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

    /** Sentinel for {@link PrimitiveArrays#atlasPage} meaning "no texture". */
    private static final int UNTEXTURED_PAGE = -1;

    /**
     * One valid triangle earmarked for a specific primitive, paired with the
     * face-range (property table row) it belongs to.
     */
    private record TriEntry(int ti, int faceIdx) {
    }

    private record PrimitiveArrays(int atlasPage, int vertexCount,
                                   float[] positions, float[] normals, float[] uvs,
                                   int[] indices, int[] featureIds,
                                   float[] posMin, float[] posMax) {
    }

    private record PrimitiveBufferIds(int positions, int normals, int uvs,
                                      int indices, int featureIds) {
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
