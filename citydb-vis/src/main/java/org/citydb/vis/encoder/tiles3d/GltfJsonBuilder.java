/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.vis.encoder.tiles3d;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONWriter;
import org.citydb.vis.model.AttrField;
import org.citydb.vis.model.AttrType;
import org.citydb.vis.model.tiles3d.MetadataProperty;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Fluent builder for the glTF 2.0 JSON chunk of a GLB file. Inputs are
 * supplied in three groups — geometry, buffer layout, metadata — and
 * {@link #build()} produces the UTF-8-encoded JSON bytes.
 * <p>
 * The builder emits the minimal glTF document needed by the 3D Tiles 1.1
 * output: one scene, one node, one mesh with a single primitive, a single
 * PBR material (textured or untextured), and the {@code EXT_mesh_features} +
 * {@code EXT_structural_metadata} extensions.
 */
final class GltfJsonBuilder {
    private static final int COMPONENT_TYPE_FLOAT = 5126;
    private static final int COMPONENT_TYPE_UNSIGNED_INT = 5125;

    // Geometry
    private int vertexCount;
    private float[] posMin;
    private float[] posMax;
    private boolean hasTexCoords;

    // Buffer layout
    private List<GltfBufferView> bufferViews;
    private int binLength;
    private int bvPositions = -1;
    private int bvNormals = -1;
    private int bvUvs = -1;
    private int bvIndices = -1;
    private int bvFeatureIds = -1;
    private int bvTexture = -1;

    // Metadata
    private int featureCount;
    private List<AttrField> attrFields;
    private List<PropertyTableBufferViews> propBvs;

    GltfJsonBuilder geometry(int vertexCount, float[] posMin, float[] posMax, boolean hasTexCoords) {
        this.vertexCount = vertexCount;
        this.posMin = posMin;
        this.posMax = posMax;
        this.hasTexCoords = hasTexCoords;
        return this;
    }

    GltfJsonBuilder bufferViews(List<GltfBufferView> bufferViews, int binLength) {
        this.bufferViews = bufferViews;
        this.binLength = binLength;
        return this;
    }

    GltfJsonBuilder geometryAccessors(int bvPositions, int bvNormals, int bvUvs,
                                      int bvIndices, int bvFeatureIds, int bvTexture) {
        this.bvPositions = bvPositions;
        this.bvNormals = bvNormals;
        this.bvUvs = bvUvs;
        this.bvIndices = bvIndices;
        this.bvFeatureIds = bvFeatureIds;
        this.bvTexture = bvTexture;
        return this;
    }

    GltfJsonBuilder metadata(int featureCount, List<AttrField> attrFields,
                             List<PropertyTableBufferViews> propBvs) {
        this.featureCount = featureCount;
        this.attrFields = attrFields;
        this.propBvs = propBvs;
        return this;
    }

    byte[] build() {
        JSONObject root = new JSONObject();
        writeAsset(root);
        writeScene(root);

        AccessorIds acc = writeAccessors(root);
        writeBufferViews(root);
        writeBuffers(root);
        writeMaterials(root);
        writeTextures(root);
        writeMeshes(root, acc);
        writeExtensions(root);

        return root.toJSONString(JSONWriter.Feature.PrettyFormatWith2Space)
                .getBytes(StandardCharsets.UTF_8);
    }

    // ---- Section writers -------------------------------------------------

    private static void writeAsset(JSONObject root) {
        JSONObject asset = new JSONObject();
        asset.put("version", "2.0");
        asset.put("generator", "3DCityDB citydb-tool");
        root.put("asset", asset);
    }

    private static void writeScene(JSONObject root) {
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
    }

    private AccessorIds writeAccessors(JSONObject root) {
        JSONArray accessors = new JSONArray();
        int accIdx = 0;

        accessors.add(makeAccessor(bvPositions, COMPONENT_TYPE_FLOAT, vertexCount, "VEC3",
                posMin, posMax));
        int accPosition = accIdx++;

        int accNormal = -1;
        if (bvNormals >= 0) {
            accessors.add(makeAccessor(bvNormals, COMPONENT_TYPE_FLOAT, vertexCount, "VEC3",
                    null, null));
            accNormal = accIdx++;
        }

        int accTexCoord = -1;
        if (hasTexCoords) {
            accessors.add(makeAccessor(bvUvs, COMPONENT_TYPE_FLOAT, vertexCount, "VEC2",
                    null, null));
            accTexCoord = accIdx++;
        }

        accessors.add(makeAccessor(bvIndices, COMPONENT_TYPE_UNSIGNED_INT, vertexCount,
                "SCALAR", null, null));
        int accIndices = accIdx++;

        accessors.add(makeAccessor(bvFeatureIds, COMPONENT_TYPE_UNSIGNED_INT, vertexCount,
                "SCALAR", null, null));
        int accFeatureId = accIdx;

        root.put("accessors", accessors);
        return new AccessorIds(accPosition, accNormal, accTexCoord, accIndices, accFeatureId);
    }

    private void writeBufferViews(JSONObject root) {
        JSONArray bvArray = new JSONArray();
        for (GltfBufferView bv : bufferViews) {
            JSONObject bvObj = new JSONObject();
            bvObj.put("buffer", 0);
            bvObj.put("byteOffset", bv.byteOffset());
            bvObj.put("byteLength", bv.byteLength());
            bvArray.add(bvObj);
        }
        root.put("bufferViews", bvArray);
    }

    private void writeBuffers(JSONObject root) {
        JSONArray buffersArr = new JSONArray();
        JSONObject buffer = new JSONObject();
        buffer.put("byteLength", binLength);
        buffersArr.add(buffer);
        root.put("buffers", buffersArr);
    }

    private void writeMaterials(JSONObject root) {
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
    }

    private void writeTextures(JSONObject root) {
        if (!hasTexCoords || bvTexture < 0) {
            return;
        }
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

    private void writeMeshes(JSONObject root, AccessorIds acc) {
        JSONArray meshes = new JSONArray();
        JSONObject mesh = new JSONObject();
        JSONArray primitives = new JSONArray();
        JSONObject primitive = new JSONObject();

        JSONObject attributes = new JSONObject();
        attributes.put("POSITION", acc.position);
        if (acc.normal >= 0) {
            attributes.put("NORMAL", acc.normal);
        }
        if (acc.texCoord >= 0) {
            attributes.put("TEXCOORD_0", acc.texCoord);
        }
        attributes.put("_FEATURE_ID_0", acc.featureId);
        primitive.put("attributes", attributes);
        primitive.put("indices", acc.indices);
        primitive.put("material", 0);

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
    }

    private void writeExtensions(JSONObject root) {
        JSONObject structMeta = buildStructuralMetadata();
        JSONObject rootExtensions = new JSONObject();
        rootExtensions.put("EXT_structural_metadata", structMeta);
        root.put("extensions", rootExtensions);

        JSONArray extUsed = new JSONArray();
        extUsed.add("EXT_mesh_features");
        extUsed.add("EXT_structural_metadata");
        root.put("extensionsUsed", extUsed);
    }

    private JSONObject buildStructuralMetadata() {
        JSONObject meta = new JSONObject();

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

        JSONArray propTables = new JSONArray();
        JSONObject propTable = new JSONObject();
        propTable.put("class", "feature");
        propTable.put("count", featureCount);

        JSONObject propTableProps = new JSONObject();
        for (int i = 0; i < attrFields.size(); i++) {
            AttrField field = attrFields.get(i);
            PropertyTableBufferViews pbv = propBvs.get(i);
            JSONObject propDef = new JSONObject();
            propDef.put("values", pbv.valuesBv());
            if (field.type() == AttrType.STRING) {
                propDef.put("stringOffsets", pbv.offsetsBv());
            }
            propTableProps.put(field.name(), propDef);
        }
        propTable.put("properties", propTableProps);
        propTables.add(propTable);
        meta.put("propertyTables", propTables);

        return meta;
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

    private record AccessorIds(int position, int normal, int texCoord, int indices, int featureId) {
    }
}
