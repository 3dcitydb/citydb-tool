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
import java.util.ArrayList;
import java.util.List;

/**
 * Fluent builder for the glTF 2.0 JSON chunk of a GLB file. Inputs are
 * supplied in four groups — buffer layout, texture, primitives, metadata —
 * and {@link #build()} produces the UTF-8-encoded JSON bytes.
 * <p>
 * The document has a single scene containing a single node that references a
 * single mesh. The mesh carries one primitive per vertex group (a textured
 * and/or untextured group) so that a node mixing textured and untextured
 * features still renders each group with the correct material. Materials
 * emitted on demand: a textured PBR material when any textured primitive is
 * present, a default PBR material when any untextured primitive is present.
 * Both primitives share the {@code EXT_structural_metadata} property table.
 */
final class GltfJsonBuilder {
    private static final int COMPONENT_TYPE_FLOAT = 5126;
    private static final int COMPONENT_TYPE_UNSIGNED_INT = 5125;

    // Buffer layout
    private List<GltfBufferView> bufferViews;
    private int binLength;

    // Shared texture (single atlas reused by all textured primitives)
    private int bvTexture = -1;

    // Primitives
    private List<Primitive> primitives = List.of();

    // Metadata
    private int featureCount;
    private List<AttrField> attrFields;
    private List<PropertyTableBufferViews> propBvs;

    GltfJsonBuilder bufferViews(List<GltfBufferView> bufferViews, int binLength) {
        this.bufferViews = bufferViews;
        this.binLength = binLength;
        return this;
    }

    GltfJsonBuilder texture(int bvTexture) {
        this.bvTexture = bvTexture;
        return this;
    }

    GltfJsonBuilder primitives(List<Primitive> primitives) {
        this.primitives = primitives;
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

        List<PrimitiveAccessors> accessors = writeAccessors(root);
        writeBufferViews(root);
        writeBuffers(root);
        MaterialIndices materials = writeMaterials(root);
        writeTextures(root);
        writeMeshes(root, accessors, materials);
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

    private List<PrimitiveAccessors> writeAccessors(JSONObject root) {
        JSONArray accessors = new JSONArray();
        List<PrimitiveAccessors> result = new ArrayList<>(primitives.size());
        int accIdx = 0;
        for (Primitive p : primitives) {
            accessors.add(makeAccessor(p.bvPositions, COMPONENT_TYPE_FLOAT, p.vertexCount,
                    "VEC3", p.posMin, p.posMax));
            int accPosition = accIdx++;

            int accNormal = -1;
            if (p.bvNormals >= 0) {
                accessors.add(makeAccessor(p.bvNormals, COMPONENT_TYPE_FLOAT, p.vertexCount,
                        "VEC3", null, null));
                accNormal = accIdx++;
            }

            int accTexCoord = -1;
            if (p.bvUvs >= 0) {
                accessors.add(makeAccessor(p.bvUvs, COMPONENT_TYPE_FLOAT, p.vertexCount,
                        "VEC2", null, null));
                accTexCoord = accIdx++;
            }

            accessors.add(makeAccessor(p.bvIndices, COMPONENT_TYPE_UNSIGNED_INT, p.vertexCount,
                    "SCALAR", null, null));
            int accIndices = accIdx++;

            accessors.add(makeAccessor(p.bvFeatureIds, COMPONENT_TYPE_UNSIGNED_INT, p.vertexCount,
                    "SCALAR", null, null));
            int accFeatureId = accIdx++;

            result.add(new PrimitiveAccessors(accPosition, accNormal, accTexCoord,
                    accIndices, accFeatureId));
        }
        root.put("accessors", accessors);
        return result;
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

    /**
     * Emit at most two PBR materials — a textured one (with a baseColorTexture
     * pointing at the shared atlas) and a default untextured one — only when
     * a primitive of that kind is actually present. Returns the indices for
     * each so {@link #writeMeshes} can reference them.
     */
    private MaterialIndices writeMaterials(JSONObject root) {
        JSONArray materials = new JSONArray();
        int texturedIdx = -1;
        int untexturedIdx = -1;
        boolean needTextured = primitives.stream().anyMatch(p -> p.textured);
        boolean needUntextured = primitives.stream().anyMatch(p -> !p.textured);

        if (needTextured) {
            JSONObject material = new JSONObject();
            JSONObject pbr = new JSONObject();
            JSONObject baseColorTexture = new JSONObject();
            baseColorTexture.put("index", 0);
            baseColorTexture.put("texCoord", 0);
            pbr.put("baseColorTexture", baseColorTexture);
            pbr.put("metallicFactor", 0.0);
            pbr.put("roughnessFactor", 1.0);
            material.put("pbrMetallicRoughness", pbr);
            texturedIdx = materials.size();
            materials.add(material);
        }

        if (needUntextured) {
            JSONObject material = new JSONObject();
            JSONObject pbr = new JSONObject();
            pbr.put("metallicFactor", 0.0);
            pbr.put("roughnessFactor", 1.0);
            material.put("pbrMetallicRoughness", pbr);
            untexturedIdx = materials.size();
            materials.add(material);
        }

        root.put("materials", materials);
        return new MaterialIndices(texturedIdx, untexturedIdx);
    }

    private void writeTextures(JSONObject root) {
        if (bvTexture < 0) {
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

    private void writeMeshes(JSONObject root, List<PrimitiveAccessors> accessors,
                             MaterialIndices materials) {
        JSONArray meshes = new JSONArray();
        JSONObject mesh = new JSONObject();
        JSONArray primitivesArr = new JSONArray();

        for (int i = 0; i < primitives.size(); i++) {
            Primitive p = primitives.get(i);
            PrimitiveAccessors acc = accessors.get(i);
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
            primitive.put("material", p.textured ? materials.textured : materials.untextured);

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

            primitivesArr.add(primitive);
        }

        mesh.put("primitives", primitivesArr);
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

    /**
     * Per-primitive geometry inputs. {@code bvNormals} and {@code bvUvs} may
     * be {@code -1} when the primitive doesn't carry that attribute —
     * textured primitives skip normals, untextured primitives skip UVs.
     */
    record Primitive(boolean textured, int vertexCount, float[] posMin, float[] posMax,
                     int bvPositions, int bvNormals, int bvUvs, int bvIndices, int bvFeatureIds) {
    }

    private record PrimitiveAccessors(int position, int normal, int texCoord,
                                      int indices, int featureId) {
    }

    private record MaterialIndices(int textured, int untextured) {
    }
}
