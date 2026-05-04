/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.encoder.tiles3d;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONWriter;
import org.citydb.vis.model.AttrField;
import org.citydb.vis.model.AttrType;
import org.citydb.vis.model.tiles3d.MetadataProperty;
import org.citydb.vis.styling.DefaultObjectStyle;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Fluent builder for the glTF 2.0 JSON chunk of a GLB file. Inputs are
 * supplied in four groups — buffer layout, textures, primitives, metadata —
 * and {@link #build()} produces the UTF-8-encoded JSON bytes.
 * <p>
 * The document has a single scene containing a single node that references
 * a single mesh. The mesh carries one primitive per atlas page (each backed
 * by its own textured PBR material), one plain primitive per distinct
 * {@link DefaultObjectStyle} used by no-appearance surfaces in the node
 * (each backed by its own PBR material), and an optional X3DMaterial-
 * colored primitive. The atlas-page split lets a node whose texture
 * footprint exceeds {@code --max-atlas-size} render across multiple pages
 * instead of being globally downscaled; the per-style plain split lets
 * per-feature-type colors render without further mesh-level work. All
 * primitives share the {@code EXT_structural_metadata} property table.
 */
final class GltfJsonBuilder {
    private static final int COMPONENT_TYPE_FLOAT = 5126;
    private static final int COMPONENT_TYPE_UNSIGNED_INT = 5125;

    // Buffer layout
    private List<GltfBufferView> bufferViews;
    private int binLength;

    // Per-page atlas bufferView indices; value {@code -1} means the page
    // exists in the input but was dropped because no primitive referenced it.
    private List<Integer> bvTextures = List.of();

    // Primitives
    private List<Primitive> primitives = List.of();

    // Metadata
    private int featureCount;
    private List<AttrField> attrFields;
    private List<PropertyTableBufferViews> propBvs;

    // Set by writeMaterials when any material gets KHR_materials_unlit applied;
    // consumed by writeExtensions to declare it in extensionsUsed.
    private boolean unlitUsed;

    GltfJsonBuilder bufferViews(List<GltfBufferView> bufferViews, int binLength) {
        this.bufferViews = bufferViews;
        this.binLength = binLength;
        return this;
    }

    GltfJsonBuilder textures(List<Integer> bvTextures) {
        this.bvTextures = bvTextures;
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

            int accColor = -1;
            if (p.bvColors >= 0) {
                accessors.add(makeAccessor(p.bvColors, COMPONENT_TYPE_FLOAT, p.vertexCount,
                        "VEC4", null, null));
                accColor = accIdx++;
            }

            accessors.add(makeAccessor(p.bvIndices, COMPONENT_TYPE_UNSIGNED_INT, p.vertexCount,
                    "SCALAR", null, null));
            int accIndices = accIdx++;

            accessors.add(makeAccessor(p.bvFeatureIds, COMPONENT_TYPE_UNSIGNED_INT, p.vertexCount,
                    "SCALAR", null, null));
            int accFeatureId = accIdx++;

            result.add(new PrimitiveAccessors(accPosition, accNormal, accTexCoord, accColor,
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
     * Emit one textured material per referenced atlas page (each with a
     * baseColorTexture pointing at its own atlas), plus up to two untextured
     * materials. The texturing/X3DMaterial paths are unlit; only the
     * no-appearance plain path is shaded:
     * <ul>
     *   <li><b>textured</b> — PBR + {@code KHR_materials_unlit}. Unlit so
     *       the texture renders at authored intensity; matches the I3S
     *       textured material (which has no NORMAL and renders unlit).</li>
     *   <li><b>plain</b> — default PBR, used by untextured primitives without
     *       X3DMaterial vertex colors. The only shaded path: surfaces get
     *       Lambertian shading from NORMAL so a default-colored building
     *       still shows 3D form. One material is emitted per distinct
     *       {@link DefaultObjectStyle} carried by the plain primitives in
     *       the node — this is how per-feature-type color overrides reach
     *       the GLB. Each material receives {@code baseColorFactor} from
     *       its style (omitted when the style still has the opaque-white
     *       default) and {@code alphaMode=BLEND} when the style's color
     *       carries alpha &lt; 1.</li>
     *   <li><b>colored</b> — PBR + {@code KHR_materials_unlit}, used by
     *       untextured primitives carrying X3DMaterial {@code COLOR_0}.
     *       Unlit because X3DMaterial in this project is used for thematic /
     *       heat-map colors where Lambertian darkening is undesirable.
     *       Flipped to {@code alphaMode=BLEND} when any colored primitive
     *       has alpha below 1 — vertex {@code COLOR_0} multiplies into
     *       baseColor so BLEND is required for transparency to render.</li>
     * </ul>
     * All three materials carry {@code doubleSided: true}. CityGML imports
     * routinely have inconsistent triangle winding on walls; the glTF default
     * (single-sided, back-face culled) would silently drop those triangles
     * from one viewing side. Mirrors the I3S writer's {@code cullFace="none"}
     * decision (see I3S {@code MaterialDefinition} javadoc).
     * Returns the resolved material indices keyed by atlas page so
     * {@link #writeMeshes} can reference them.
     */
    private MaterialIndices writeMaterials(JSONObject root) {
        JSONArray materials = new JSONArray();
        int pageCount = bvTextures.size();
        int[] texturedIdx = new int[pageCount];
        Arrays.fill(texturedIdx, -1);
        int untexturedColoredIdx = -1;

        // Pass 1: scan primitives to discover which textured pages are in
        // use, the set of distinct plain styles (in encounter order so the
        // emitted material order is stable across runs), and whether any
        // colored primitive carries below-1 alpha.
        boolean[] pageUsed = new boolean[pageCount];
        LinkedHashSet<DefaultObjectStyle> plainStyles = new LinkedHashSet<>();
        boolean needColored = false;
        boolean coloredNeedsBlend = false;
        for (Primitive p : primitives) {
            if (p.atlasPage >= 0) {
                pageUsed[p.atlasPage] = true;
            } else if (p.bvColors >= 0) {
                needColored = true;
                if (p.anyAlphaBelowOne) {
                    coloredNeedsBlend = true;
                }
            } else {
                // Plain primitive — track its style. plainStyle is required
                // to be non-null on plain primitives by the encoder contract.
                plainStyles.add(p.plainStyle);
            }
        }

        // Pass 2: emit materials in fixed order — textured (per atlas
        // page), plain (per distinct style), colored — and record indices.
        int textureSlot = 0;
        for (int p = 0; p < pageCount; p++) {
            if (!pageUsed[p]) continue;
            JSONObject material = new JSONObject();
            JSONObject pbr = new JSONObject();
            JSONObject baseColorTexture = new JSONObject();
            baseColorTexture.put("index", textureSlot++);
            baseColorTexture.put("texCoord", 0);
            pbr.put("baseColorTexture", baseColorTexture);
            pbr.put("metallicFactor", 0.0);
            pbr.put("roughnessFactor", 1.0);
            material.put("pbrMetallicRoughness", pbr);
            material.put("doubleSided", true);
            JSONObject extensions = new JSONObject();
            extensions.put("KHR_materials_unlit", new JSONObject());
            material.put("extensions", extensions);
            unlitUsed = true;
            texturedIdx[p] = materials.size();
            materials.add(material);
        }

        Map<DefaultObjectStyle, Integer> plainIdxByStyle = new HashMap<>(plainStyles.size() * 2);
        for (DefaultObjectStyle style : plainStyles) {
            JSONObject material = new JSONObject();
            JSONObject pbr = new JSONObject();
            pbr.put("metallicFactor", 0.0);
            pbr.put("roughnessFactor", 1.0);
            // baseColorFactor: only emit when the style is non-default
            // opaque white; the glTF default is opaque white so omitting
            // the field keeps the JSON minimal in the common case.
            if (style.hasNonDefaultColor()) {
                JSONArray baseColorFactor = new JSONArray();
                for (float v : style.toLinearRgba()) baseColorFactor.add(v);
                pbr.put("baseColorFactor", baseColorFactor);
                if (style.hasAlpha()) {
                    material.put("alphaMode", "BLEND");
                }
            }
            material.put("pbrMetallicRoughness", pbr);
            material.put("doubleSided", true);
            plainIdxByStyle.put(style, materials.size());
            materials.add(material);
        }

        if (needColored) {
            JSONObject material = new JSONObject();
            JSONObject pbr = new JSONObject();
            pbr.put("metallicFactor", 0.0);
            pbr.put("roughnessFactor", 1.0);
            material.put("pbrMetallicRoughness", pbr);
            if (coloredNeedsBlend) {
                material.put("alphaMode", "BLEND");
            }
            material.put("doubleSided", true);
            JSONObject extensions = new JSONObject();
            extensions.put("KHR_materials_unlit", new JSONObject());
            material.put("extensions", extensions);
            unlitUsed = true;
            untexturedColoredIdx = materials.size();
            materials.add(material);
        }

        root.put("materials", materials);
        return new MaterialIndices(texturedIdx, plainIdxByStyle, untexturedColoredIdx);
    }

    /**
     * Emit one {@code images}/{@code textures} entry per referenced atlas
     * page, all sharing a single sampler. Texture indices are assigned
     * densely in page order so they line up with the {@code textureSlot}
     * counter used by {@link #writeMaterials}.
     */
    private void writeTextures(JSONObject root) {
        JSONArray images = new JSONArray();
        JSONArray textures = new JSONArray();
        for (int bv : bvTextures) {
            if (bv < 0) continue;
            JSONObject image = new JSONObject();
            image.put("bufferView", bv);
            image.put("mimeType", "image/jpeg");
            images.add(image);

            JSONObject texture = new JSONObject();
            texture.put("sampler", 0);
            texture.put("source", images.size() - 1);
            textures.add(texture);
        }
        if (textures.isEmpty()) {
            return;
        }

        JSONArray samplers = new JSONArray();
        JSONObject sampler = new JSONObject();
        sampler.put("magFilter", 9729); // LINEAR
        sampler.put("minFilter", 9987); // LINEAR_MIPMAP_LINEAR
        samplers.add(sampler);
        root.put("samplers", samplers);

        root.put("images", images);
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
            if (acc.color >= 0) {
                attributes.put("COLOR_0", acc.color);
            }
            attributes.put("_FEATURE_ID_0", acc.featureId);
            primitive.put("attributes", attributes);
            primitive.put("indices", acc.indices);
            int materialIdx;
            if (p.atlasPage >= 0) {
                materialIdx = materials.textured[p.atlasPage];
            } else if (p.bvColors >= 0) {
                materialIdx = materials.untexturedColored;
            } else {
                materialIdx = materials.untexturedPlainByStyle.get(p.plainStyle);
            }
            primitive.put("material", materialIdx);

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
        if (unlitUsed) {
            extUsed.add("KHR_materials_unlit");
        }
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
     * Per-primitive geometry inputs. {@code atlasPage >= 0} selects the
     * textured path (material indexed by atlas page); negative sentinels
     * select one of the two untextured materials (plain PBR or unlit
     * colored). {@code bvNormals}, {@code bvUvs} and {@code bvColors} may
     * be {@code -1} when the primitive doesn't carry that attribute — only
     * the untextured-plain path emits NORMAL; textured primitives carry UV
     * (no normal, no color); X3DMaterial-colored primitives carry COLOR_0
     * (no normal, no UV). {@code anyAlphaBelowOne} flips the untextured
     * colored material to {@code alphaMode=BLEND}.
     */
    record Primitive(int atlasPage, int vertexCount, float[] posMin, float[] posMax,
                     int bvPositions, int bvNormals, int bvUvs, int bvColors,
                     int bvIndices, int bvFeatureIds,
                     boolean anyAlphaBelowOne,
                     DefaultObjectStyle plainStyle) {
    }

    private record PrimitiveAccessors(int position, int normal, int texCoord, int color,
                                      int indices, int featureId) {
    }

    /**
     * Resolved glTF material indices. {@code textured[p]} is the material
     * index for atlas page {@code p}, or {@code -1} if that page wasn't
     * referenced. {@code untexturedPlainByStyle} maps each plain primitive's
     * {@link DefaultObjectStyle} to the material index that carries that
     * style's {@code baseColorFactor}; one entry per distinct style used by
     * a plain primitive in this GLB. {@code untexturedColored} is {@code -1}
     * when no X3DMaterial-colored primitives are present.
     */
    private record MaterialIndices(int[] textured,
                                   Map<DefaultObjectStyle, Integer> untexturedPlainByStyle,
                                   int untexturedColored) {
    }
}
