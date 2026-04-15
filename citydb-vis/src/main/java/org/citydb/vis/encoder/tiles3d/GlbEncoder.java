/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.vis.encoder.tiles3d;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONWriter;
import org.citydb.vis.encoder.AttrValueCoercer;
import org.citydb.vis.geometry.TriangleMesh;
import org.citydb.vis.geometry.VertexWelder;
import org.citydb.vis.model.AttrField;
import org.citydb.vis.model.AttrType;
import org.citydb.vis.model.FeatureData;
import org.citydb.vis.model.tiles3d.MetadataProperty;
import org.citydb.vis.scene.BoundingVolume;
import org.citydb.vis.scene.SceneNode;
import org.citydb.vis.util.BufferUtils;
import org.citydb.vis.util.GeoTransform;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Encodes a scene node's geometry, textures, and per-feature metadata into a
 * glTF 2.0 Binary (GLB) file without external library dependencies.
 * <p>
 * Each GLB contains:
 * <ul>
 *   <li>Mesh geometry: positions (local ENU), normals, optional UVs, indices</li>
 *   <li>Texture: JPEG atlas embedded as buffer view (if textured)</li>
 *   <li>{@code EXT_mesh_features}: per-vertex feature ID attribute</li>
 *   <li>{@code EXT_structural_metadata}: per-feature property table</li>
 * </ul>
 * <p>
 * Positions are in a local ENU coordinate frame relative to a dataset center.
 * The root tile's {@code transform} in {@code tileset.json} converts ENU to ECEF.
 */
public class GlbEncoder {
    private static final int GLB_MAGIC = 0x46546C67;  // "glTF"
    private static final int GLB_VERSION = 2;
    private static final int CHUNK_JSON = 0x4E4F534A;  // "JSON"
    private static final int CHUNK_BIN = 0x004E4942;   // "BIN\0"

    /**
     * Encode a mesh node into a GLB byte array. Returns {@code null} if the
     * mesh is empty after welding/degenerate filtering.
     *
     * @param node          scene node (mesh will be cleared after encoding)
     * @param textureBytes  JPEG atlas bytes, or {@code null} if untextured
     * @param features      per-feature attribute data
     * @param attrFields    finalized attribute field definitions
     * @param datasetCenter [centerLon, centerLat, centerAlt] of the dataset
     * @return GLB bytes, or {@code null} if empty
     */
    public byte[] encode(SceneNode node, byte[] textureBytes,
                         List<FeatureData> features, List<AttrField> attrFields,
                         double[] datasetCenter) throws IOException {
        TriangleMesh mesh = node.getMesh();
        boolean hasTexCoords = mesh.hasTexCoords() && textureBytes != null;
        if (!hasTexCoords) {
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
        VertexArrays arrays = buildVertexArrays(mesh, weld, hasTexCoords, frame);
        node.setMesh(null); // release source mesh; no longer needed

        BinBufferBuilder bin = new BinBufferBuilder();
        BufferViewIds bvIds = writeGeometryBuffers(bin, arrays, hasTexCoords, textureBytes);

        int featureCount = weld.faceRanges().size();
        List<FeatureData> propFeatures = featureCount < features.size()
                ? FeatureData.reorderByIds(features, weld.rangeFeatureIds())
                : features;
        List<PropertyTableBufferViews> propBvs = new ArrayList<>();
        for (AttrField field : attrFields) {
            propBvs.add(encodePropertyField(bin, field, propFeatures));
        }

        byte[] binData = bin.toByteArray();
        byte[] jsonData = buildGltfJson(arrays.vertexCount, arrays.vertexCount / 3, featureCount,
                hasTexCoords, arrays.posMin, arrays.posMax, bin.getBufferViews(),
                bvIds.positions, bvIds.normals, bvIds.uvs, bvIds.indices, bvIds.featureIds,
                bvIds.texture, attrFields, propBvs, binData.length);

        return assembleGlb(jsonData, binData);
    }

    /**
     * Rewrites welded vertex data from ENU (meters, East/North/Up) into glTF Y-up
     * (X=East, Y=Up, Z=-North), collects per-axis min/max for the POSITION
     * accessor, and selects normals/UVs per the texturing mode.
     */
    private static VertexArrays buildVertexArrays(TriangleMesh mesh, VertexWelder.WeldResult weld,
                                                  boolean hasTexCoords, DatasetFrame frame) {
        int vertexCount = weld.vertexCount();
        float[][] weldedPositions = weld.weldedPositions();
        List<int[]> allTriangles = mesh.getTriangles();

        // Untextured nodes carry normals; textured nodes let CesiumJS generate
        // flat normals from geometry (matches I3S behaviour, better for buildings).
        float[] positions = new float[vertexCount * 3];
        float[] normals = hasTexCoords ? null : new float[vertexCount * 3];
        float[] uvs = hasTexCoords ? new float[vertexCount * 2] : null;
        int[] indices = new int[vertexCount]; // triangle soup: 0,1,2,3,...
        float[] posMin = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE};
        float[] posMax = {-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};

        int idx = 0;
        for (int ti : weld.validTriIndices()) {
            int base = ti * 3;
            int[] tri = allTriangles.get(ti);
            for (int j = 0; j < 3; j++) {
                float[] wp = weldedPositions[base + j];
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
                    float[] n = mesh.getNormals().get(tri[j]);
                    normals[idx * 3] = n[0];
                    normals[idx * 3 + 1] = n[2];
                    normals[idx * 3 + 2] = -n[1];
                }
                if (uvs != null) {
                    float[] uv = mesh.getTexCoords().get(tri[j]);
                    uvs[idx * 2] = uv[0];
                    uvs[idx * 2 + 1] = uv[1];
                }

                indices[idx] = idx;
                idx++;
            }
        }

        return new VertexArrays(vertexCount, positions, normals, uvs,
                indices, weld.computeFeatureIndices(), posMin, posMax);
    }

    /** Writes geometry arrays into the BIN buffer and returns their buffer view ids. */
    private static BufferViewIds writeGeometryBuffers(BinBufferBuilder bin, VertexArrays a,
                                                      boolean hasTexCoords, byte[] textureBytes) {
        int bvPositions = bin.addFloat32Array(a.positions);
        int bvNormals = a.normals != null ? bin.addFloat32Array(a.normals) : -1;
        int bvUvs = hasTexCoords ? bin.addFloat32Array(a.uvs) : -1;
        int bvIndices = bin.addUint32Array(a.indices);
        int bvFeatureIds = bin.addUint32Array(a.featureIds);
        int bvTexture = (textureBytes != null && hasTexCoords) ? bin.addRawBytes(textureBytes) : -1;
        return new BufferViewIds(bvPositions, bvNormals, bvUvs, bvIndices, bvFeatureIds, bvTexture);
    }

    /** Assembles the final GLB container: header + 4-byte-aligned JSON and BIN chunks. */
    private static byte[] assembleGlb(byte[] jsonData, byte[] binData) {
        int jsonPadding = (4 - (jsonData.length % 4)) % 4;
        int jsonChunkLength = jsonData.length + jsonPadding;
        int binPadding = (4 - (binData.length % 4)) % 4;
        int binChunkLength = binData.length + binPadding;

        // GLB = header(12) + JSON chunk header(8) + JSON data + BIN chunk header(8) + BIN data
        int totalLength = 12 + 8 + jsonChunkLength + 8 + binChunkLength;
        ByteBuffer glb = BufferUtils.allocateLittleEndian(totalLength);

        glb.putInt(GLB_MAGIC);
        glb.putInt(GLB_VERSION);
        glb.putInt(totalLength);

        glb.putInt(jsonChunkLength);
        glb.putInt(CHUNK_JSON);
        glb.put(jsonData);
        for (int i = 0; i < jsonPadding; i++) glb.put((byte) ' ');

        glb.putInt(binChunkLength);
        glb.putInt(CHUNK_BIN);
        glb.put(binData);
        for (int i = 0; i < binPadding; i++) glb.put((byte) 0);

        return glb.array();
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

    private record VertexArrays(int vertexCount, float[] positions, float[] normals, float[] uvs,
                                int[] indices, int[] featureIds, float[] posMin, float[] posMax) {
    }

    private record BufferViewIds(int positions, int normals, int uvs,
                                 int indices, int featureIds, int texture) {
    }

    // ---- glTF JSON construction -----------------------------------------

    private static byte[] buildGltfJson(int vertexCount, int triangleCount, int featureCount,
                                        boolean hasTexCoords, float[] posMin, float[] posMax,
                                        List<int[]> bufferViews,
                                        int bvPositions, int bvNormals, int bvUvs,
                                        int bvIndices, int bvFeatureIds, int bvTexture,
                                        List<AttrField> attrFields,
                                        List<PropertyTableBufferViews> propBvs,
                                        int binLength) {
        JSONObject root = new JSONObject();

        // asset
        JSONObject asset = new JSONObject();
        asset.put("version", "2.0");
        asset.put("generator", "3DCityDB citydb-tool");
        root.put("asset", asset);

        // scene / scenes / nodes
        root.put("scene", 0);
        JSONArray scenes = new JSONArray();
        JSONObject scene = new JSONObject();
        scene.put("nodes", new JSONArray().fluentAdd(0));
        scenes.add(scene);
        root.put("scenes", scenes);

        JSONArray nodes = new JSONArray();
        JSONObject node = new JSONObject();
        node.put("mesh", 0);
        nodes.add(node);
        root.put("nodes", nodes);

        // accessors
        JSONArray accessors = new JSONArray();
        int accIdx = 0;

        // Accessor 0: POSITION
        accessors.add(makeAccessor(bvPositions, 5126, vertexCount, "VEC3",
                new float[]{posMin[0], posMin[1], posMin[2]},
                new float[]{posMax[0], posMax[1], posMax[2]}));
        int accPosition = accIdx++;

        // Accessor: NORMAL (untextured only)
        int accNormal = -1;
        if (bvNormals >= 0) {
            accessors.add(makeAccessor(bvNormals, 5126, vertexCount, "VEC3", null, null));
            accNormal = accIdx++;
        }

        // Accessor: TEXCOORD_0 (optional)
        int accTexCoord = -1;
        if (hasTexCoords) {
            accessors.add(makeAccessor(bvUvs, 5126, vertexCount, "VEC2", null, null));
            accTexCoord = accIdx++;
        }

        // Accessor N: indices
        accessors.add(makeAccessor(bvIndices, 5125, vertexCount, "SCALAR", null, null));
        int accIndices = accIdx++;

        // Accessor N+1: _FEATURE_ID_0
        accessors.add(makeAccessor(bvFeatureIds, 5125, vertexCount, "SCALAR", null, null));
        int accFeatureId = accIdx;

        root.put("accessors", accessors);

        // bufferViews
        JSONArray bvArray = new JSONArray();
        for (int[] bv : bufferViews) {
            JSONObject bvObj = new JSONObject();
            bvObj.put("buffer", 0);
            bvObj.put("byteOffset", bv[0]);
            bvObj.put("byteLength", bv[1]);
            bvArray.add(bvObj);
        }
        root.put("bufferViews", bvArray);

        // buffers
        JSONArray buffersArr = new JSONArray();
        JSONObject buffer = new JSONObject();
        buffer.put("byteLength", binLength);
        buffersArr.add(buffer);
        root.put("buffers", buffersArr);

        // materials
        JSONArray materials = new JSONArray();
        JSONObject material = new JSONObject();
        JSONObject pbr = new JSONObject();
        if (hasTexCoords) {
            JSONObject baseColorTexture = new JSONObject();
            baseColorTexture.put("index", 0);
            baseColorTexture.put("texCoord", 0);
            pbr.put("baseColorTexture", baseColorTexture);
        }
        pbr.put("metallicFactor", 0.0);
        pbr.put("roughnessFactor", 1.0);
        material.put("pbrMetallicRoughness", pbr);
        materials.add(material);
        root.put("materials", materials);

        // textures, images, samplers (if textured)
        if (hasTexCoords && bvTexture >= 0) {
            JSONArray samplers = new JSONArray();
            JSONObject sampler = new JSONObject();
            sampler.put("magFilter", 9729); // LINEAR
            sampler.put("minFilter", 9987); // LINEAR_MIPMAP_LINEAR
            samplers.add(sampler);
            root.put("samplers", samplers);

            JSONArray images = new JSONArray();
            JSONObject image = new JSONObject();
            image.put("bufferView", bvTexture);
            image.put("mimeType", "image/jpeg");
            images.add(image);
            root.put("images", images);

            JSONArray textures = new JSONArray();
            JSONObject texture = new JSONObject();
            texture.put("sampler", 0);
            texture.put("source", 0);
            textures.add(texture);
            root.put("textures", textures);
        }

        // meshes with EXT_mesh_features
        JSONArray meshes = new JSONArray();
        JSONObject mesh = new JSONObject();
        JSONArray primitives = new JSONArray();
        JSONObject primitive = new JSONObject();

        JSONObject attributes = new JSONObject();
        attributes.put("POSITION", accPosition);
        if (accNormal >= 0) {
            attributes.put("NORMAL", accNormal);
        }
        if (accTexCoord >= 0) {
            attributes.put("TEXCOORD_0", accTexCoord);
        }
        attributes.put("_FEATURE_ID_0", accFeatureId);
        primitive.put("attributes", attributes);
        primitive.put("indices", accIndices);
        primitive.put("material", 0);

        // EXT_mesh_features
        JSONObject meshFeaturesExt = new JSONObject();
        JSONArray featureIdsArr = new JSONArray();
        JSONObject featureIdDef = new JSONObject();
        featureIdDef.put("featureCount", featureCount);
        featureIdDef.put("attribute", 0);
        featureIdDef.put("propertyTable", 0);
        featureIdsArr.add(featureIdDef);
        meshFeaturesExt.put("featureIds", featureIdsArr);

        JSONObject primExtensions = new JSONObject();
        primExtensions.put("EXT_mesh_features", meshFeaturesExt);
        primitive.put("extensions", primExtensions);

        primitives.add(primitive);
        mesh.put("primitives", primitives);
        meshes.add(mesh);
        root.put("meshes", meshes);

        // EXT_structural_metadata (root-level extension)
        JSONObject structMeta = buildStructuralMetadata(attrFields, propBvs, featureCount);
        JSONObject rootExtensions = new JSONObject();
        rootExtensions.put("EXT_structural_metadata", structMeta);
        root.put("extensions", rootExtensions);

        // extensionsUsed
        JSONArray extUsed = new JSONArray();
        extUsed.add("EXT_mesh_features");
        extUsed.add("EXT_structural_metadata");
        root.put("extensionsUsed", extUsed);

        return root.toJSONString(JSONWriter.Feature.PrettyFormatWith2Space)
                .getBytes(StandardCharsets.UTF_8);
    }

    private static JSONObject makeAccessor(int bufferView, int componentType,
                                           int count, String type,
                                           float[] min, float[] max) {
        JSONObject acc = new JSONObject();
        acc.put("bufferView", bufferView);
        acc.put("componentType", componentType);
        acc.put("count", count);
        acc.put("type", type);
        if (min != null) {
            JSONArray minArr = new JSONArray();
            for (float v : min) minArr.add(v);
            acc.put("min", minArr);
        }
        if (max != null) {
            JSONArray maxArr = new JSONArray();
            for (float v : max) maxArr.add(v);
            acc.put("max", maxArr);
        }
        return acc;
    }

    // ---- EXT_structural_metadata ----------------------------------------

    private static JSONObject buildStructuralMetadata(List<AttrField> attrFields,
                                                      List<PropertyTableBufferViews> propBvs,
                                                      int featureCount) {
        JSONObject meta = new JSONObject();

        // Schema
        JSONObject schema = new JSONObject();
        JSONObject classes = new JSONObject();
        JSONObject featureClass = new JSONObject();
        JSONObject properties = new JSONObject();
        for (AttrField field : attrFields) {
            JSONObject prop = new JSONObject();
            prop.put("type", MetadataProperty.tilesType(field.type()));
            properties.put(field.name(), prop);
        }
        featureClass.put("properties", properties);
        classes.put("feature", featureClass);
        schema.put("classes", classes);
        meta.put("schema", schema);

        // Property tables
        JSONArray propTables = new JSONArray();
        JSONObject propTable = new JSONObject();
        propTable.put("class", "feature");
        propTable.put("count", featureCount);

        JSONObject propTableProps = new JSONObject();
        for (int i = 0; i < attrFields.size(); i++) {
            AttrField field = attrFields.get(i);
            PropertyTableBufferViews pbv = propBvs.get(i);
            JSONObject propDef = new JSONObject();
            propDef.put("values", pbv.valuesBv);
            if (field.type() == AttrType.STRING) {
                propDef.put("stringOffsets", pbv.offsetsBv);
            }
            propTableProps.put(field.name(), propDef);
        }
        propTable.put("properties", propTableProps);
        propTables.add(propTable);
        meta.put("propertyTables", propTables);

        return meta;
    }

    // ---- Property table encoding ----------------------------------------

    private static PropertyTableBufferViews encodePropertyField(
            BinBufferBuilder bin, AttrField field, List<FeatureData> features) {
        return switch (field.type()) {
            case OID -> encodeOidProperty(bin, features);
            case INT -> encodeIntProperty(bin, field.name(), features);
            case DOUBLE -> encodeDoubleProperty(bin, field.name(), features);
            case STRING -> encodeStringProperty(bin, field.name(), features);
        };
    }

    private static PropertyTableBufferViews encodeOidProperty(
            BinBufferBuilder bin, List<FeatureData> features) {
        int[] values = new int[features.size()];
        for (int i = 0; i < features.size(); i++) {
            values[i] = (int) features.get(i).id();
        }
        int bv = bin.addInt32Array(values);
        return new PropertyTableBufferViews(bv, -1);
    }

    private static PropertyTableBufferViews encodeIntProperty(
            BinBufferBuilder bin, String fieldName, List<FeatureData> features) {
        int[] values = new int[features.size()];
        for (int i = 0; i < features.size(); i++) {
            values[i] = AttrValueCoercer.toInt(features.get(i).getFieldValue(fieldName));
        }
        int bv = bin.addInt32Array(values);
        return new PropertyTableBufferViews(bv, -1);
    }

    private static PropertyTableBufferViews encodeDoubleProperty(
            BinBufferBuilder bin, String fieldName, List<FeatureData> features) {
        double[] values = new double[features.size()];
        for (int i = 0; i < features.size(); i++) {
            values[i] = AttrValueCoercer.toDouble(features.get(i).getFieldValue(fieldName));
        }
        int bv = bin.addFloat64Array(values);
        return new PropertyTableBufferViews(bv, -1);
    }

    private static PropertyTableBufferViews encodeStringProperty(
            BinBufferBuilder bin, String fieldName, List<FeatureData> features) {
        // Concatenate all string values and build offset array
        ByteArrayOutputStream valuesStream = new ByteArrayOutputStream();
        int[] offsets = new int[features.size() + 1];
        int offset = 0;
        for (int i = 0; i < features.size(); i++) {
            offsets[i] = offset;
            byte[] bytes = AttrValueCoercer.toUtf8(features.get(i).getFieldValue(fieldName));
            valuesStream.writeBytes(bytes);
            offset += bytes.length;
        }
        offsets[features.size()] = offset;

        int valuesBv = bin.addRawBytes(valuesStream.toByteArray());
        int offsetsBv = bin.addUint32Array(offsets);
        return new PropertyTableBufferViews(valuesBv, offsetsBv);
    }


    // ---- Helper types ---------------------------------------------------

    private record PropertyTableBufferViews(int valuesBv, int offsetsBv) {
    }

    /**
     * Builds the BIN chunk data by sequentially appending typed arrays.
     * Each segment is 4-byte aligned. Tracks buffer view metadata.
     */
    static class BinBufferBuilder {
        private final ByteArrayOutputStream data = new ByteArrayOutputStream();
        private final List<int[]> bufferViews = new ArrayList<>();

        /** Add a float32 array. Returns the buffer view index. */
        int addFloat32Array(float[] values) {
            align4();
            int offset = data.size();
            int byteLength = values.length * 4;
            ByteBuffer buf = BufferUtils.allocateLittleEndian(byteLength);
            for (float v : values) buf.putFloat(v);
            data.writeBytes(buf.array());
            bufferViews.add(new int[]{offset, byteLength});
            return bufferViews.size() - 1;
        }

        /** Add a uint32/int32 array. Returns the buffer view index. */
        int addUint32Array(int[] values) {
            align4();
            int offset = data.size();
            int byteLength = values.length * 4;
            ByteBuffer buf = BufferUtils.allocateLittleEndian(byteLength);
            for (int v : values) buf.putInt(v);
            data.writeBytes(buf.array());
            bufferViews.add(new int[]{offset, byteLength});
            return bufferViews.size() - 1;
        }

        /** Add an int32 array (same binary layout as uint32). */
        int addInt32Array(int[] values) {
            return addUint32Array(values);
        }

        /** Add a float64 array (8-byte aligned). Returns the buffer view index. */
        int addFloat64Array(double[] values) {
            align(8);
            int offset = data.size();
            int byteLength = values.length * 8;
            ByteBuffer buf = BufferUtils.allocateLittleEndian(byteLength);
            for (double v : values) buf.putDouble(v);
            data.writeBytes(buf.array());
            bufferViews.add(new int[]{offset, byteLength});
            return bufferViews.size() - 1;
        }

        /** Add raw bytes (e.g., JPEG image data). Returns the buffer view index. */
        int addRawBytes(byte[] bytes) {
            align4();
            int offset = data.size();
            data.writeBytes(bytes);
            bufferViews.add(new int[]{offset, bytes.length});
            return bufferViews.size() - 1;
        }

        List<int[]> getBufferViews() {
            return bufferViews;
        }

        byte[] toByteArray() {
            return data.toByteArray();
        }

        private void align4() {
            align(4);
        }

        private void align(int boundary) {
            int pad = (boundary - (data.size() % boundary)) % boundary;
            for (int i = 0; i < pad; i++) {
                data.write(0);
            }
        }
    }
}
