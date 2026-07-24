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
import org.citydb.vis.model.tiles3d.MetadataSchema;
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
 * The document has a single scene containing the merged-mesh node (present
 * when the node has explicit geometry) followed by one node+mesh per
 * GPU-instanced (prototype × style) group, each carrying
 * {@code EXT_mesh_gpu_instancing} + {@code EXT_instance_features}. The
 * merged mesh carries one primitive per atlas page (each backed by its own
 * textured PBR material), one plain primitive per distinct
 * {@link DefaultObjectStyle} used by no-appearance surfaces in the node
 * (each backed by its own PBR material), and an optional X3DMaterial-
 * colored primitive. The atlas-page split lets a node whose texture
 * footprint exceeds {@code --max-atlas-size} render across multiple pages
 * instead of being globally downscaled; the per-style plain split lets
 * per-feature-type colors render without further mesh-level work. All
 * primitives — mesh-backed and instanced — share the
 * {@code EXT_structural_metadata} property table.
 */
final class GltfJsonBuilder {
    private static final int COMPONENT_TYPE_FLOAT = 5126;
    private static final int COMPONENT_TYPE_UNSIGNED_INT = 5125;
    /**
     * {@code Primitive.atlasPage} sentinel for an instanced prototype's
     * textured primitive: it samples one of the prototype's own atlas pages
     * (embedded once per GLB and referenced per primitive via
     * {@code Primitive.bvInstancedAtlas}), not one of the node's atlas pages.
     * Owned here because this class does the material routing on it;
     * {@code GlbPrimitiveBuilder} and {@code InstancedNodeEncoder} reference
     * it directly.
     */
    static final int INSTANCED_TEXTURED_PAGE = -3;

    // Buffer layout
    private List<GltfBufferView> bufferViews;
    private int binLength;

    // Per-page atlas bufferView indices; value {@code -1} means the page
    // exists in the input but was dropped because no primitive referenced it.
    private List<Integer> bvTextures = List.of();

    // Primitives
    private List<Primitive> primitives = List.of();

    // GPU-instanced nodes (one per prototype × style group); empty when the
    // node carries no instanced content.
    private List<InstancedNode> instancedNodes = List.of();

    // Metadata. propertyRowCount = total property-table rows (the main
    // mesh's face-range rows plus per-instance rows appended after them);
    // each primitive's EXT_mesh_features featureCount is carried on the
    // Primitive itself (spec: unique features per feature-id attribute).
    private int propertyRowCount;
    private List<AttrField> attrFields;
    private List<PropertyTableBufferViews> propBvs;

    // Mirrors --enable-shading at the writer level. When false, every
    // material carries KHR_materials_unlit alongside its primitives'
    // missing NORMAL attribute (so CesiumJS doesn't auto-derive flat
    // normals); when true every material renders PBR-shaded. Textured
    // primitives use the up-direction normal trick to keep walls and roofs
    // equally lit (see GlbEncoder javadoc), matching the I3S writer.
    private boolean enableShading;

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

    GltfJsonBuilder instancedNodes(List<InstancedNode> instancedNodes) {
        this.instancedNodes = instancedNodes;
        return this;
    }

    GltfJsonBuilder metadata(int propertyRowCount,
                             List<AttrField> attrFields,
                             List<PropertyTableBufferViews> propBvs) {
        this.propertyRowCount = propertyRowCount;
        this.attrFields = attrFields;
        this.propBvs = propBvs;
        return this;
    }

    GltfJsonBuilder enableShading(boolean enableShading) {
        this.enableShading = enableShading;
        return this;
    }

    byte[] build() {
        JSONObject root = new JSONObject();
        writeAsset(root);

        AccessorLayout accessors = writeAccessors(root);
        writeScene(root, accessors);
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

    /**
     * Emit the scene graph: one node for the merged mesh (index 0, present
     * only when the node has main primitives) followed by one node per
     * instanced batch, each carrying {@code EXT_mesh_gpu_instancing} (TRS +
     * per-instance {@code _FEATURE_ID_0} attributes) and
     * {@code EXT_instance_features} referencing the shared property table.
     * Mesh indices parallel node order.
     */
    private void writeScene(JSONObject root, AccessorLayout accessors) {
        root.put("scene", 0);
        JSONArray sceneNodes = new JSONArray();
        JSONArray nodes = new JSONArray();

        if (!primitives.isEmpty()) {
            JSONObject node = new JSONObject();
            node.put("mesh", nodes.size());
            sceneNodes.add(nodes.size());
            nodes.add(node);
        }
        for (int i = 0; i < instancedNodes.size(); i++) {
            InstancedNode in = instancedNodes.get(i);
            int[] trs = accessors.instancedTrs.get(i);
            JSONObject node = new JSONObject();
            node.put("mesh", nodes.size());

            JSONObject instancingExt = new JSONObject();
            JSONObject instAttributes = new JSONObject();
            instAttributes.put("TRANSLATION", trs[0]);
            instAttributes.put("ROTATION", trs[1]);
            instAttributes.put("SCALE", trs[2]);
            instAttributes.put("_FEATURE_ID_0", trs[3]);
            instancingExt.put("attributes", instAttributes);

            JSONObject instanceFeaturesExt = new JSONObject();
            JSONArray featureIdsArr = new JSONArray();
            JSONObject featureIdDef = new JSONObject();
            featureIdDef.put("featureCount", in.instanceCount);
            featureIdDef.put("attribute", 0);
            featureIdDef.put("propertyTable", 0);
            featureIdsArr.add(featureIdDef);
            instanceFeaturesExt.put("featureIds", featureIdsArr);

            JSONObject nodeExtensions = new JSONObject();
            nodeExtensions.put("EXT_mesh_gpu_instancing", instancingExt);
            nodeExtensions.put("EXT_instance_features", instanceFeaturesExt);
            node.put("extensions", nodeExtensions);

            sceneNodes.add(nodes.size());
            nodes.add(node);
        }

        JSONArray scenes = new JSONArray();
        JSONObject scene = new JSONObject();
        scene.put("nodes", sceneNodes);
        scenes.add(scene);
        root.put("scenes", scenes);
        root.put("nodes", nodes);
    }

    private AccessorLayout writeAccessors(JSONObject root) {
        JSONArray accessors = new JSONArray();
        List<PrimitiveAccessors> main = new ArrayList<>(primitives.size());
        for (Primitive p : primitives) {
            main.add(addPrimitiveAccessors(accessors, p));
        }

        List<List<PrimitiveAccessors>> instanced = new ArrayList<>(instancedNodes.size());
        List<int[]> instancedTrs = new ArrayList<>(instancedNodes.size());
        for (InstancedNode in : instancedNodes) {
            List<PrimitiveAccessors> perPrim = new ArrayList<>(in.primitives.size());
            for (Primitive p : in.primitives) {
                perPrim.add(addPrimitiveAccessors(accessors, p));
            }
            instanced.add(perPrim);

            accessors.add(makeAccessor(in.bvTranslation, COMPONENT_TYPE_FLOAT,
                    in.instanceCount, "VEC3", in.tMin, in.tMax));
            int accT = accessors.size() - 1;
            accessors.add(makeAccessor(in.bvRotation, COMPONENT_TYPE_FLOAT,
                    in.instanceCount, "VEC4", null, null));
            int accR = accessors.size() - 1;
            accessors.add(makeAccessor(in.bvScale, COMPONENT_TYPE_FLOAT,
                    in.instanceCount, "VEC3", null, null));
            int accS = accessors.size() - 1;
            // Per-instance feature ids share the FLOAT widening rationale of
            // the per-vertex attribute (integral property-table row indices).
            accessors.add(makeAccessor(in.bvFeatureIds, COMPONENT_TYPE_FLOAT,
                    in.instanceCount, "SCALAR", null, null));
            int accF = accessors.size() - 1;
            instancedTrs.add(new int[]{accT, accR, accS, accF});
        }
        root.put("accessors", accessors);
        return new AccessorLayout(main, instanced, instancedTrs);
    }

    private static PrimitiveAccessors addPrimitiveAccessors(JSONArray accessors, Primitive p) {
        accessors.add(makeAccessor(p.bvPositions, COMPONENT_TYPE_FLOAT, p.vertexCount,
                "VEC3", p.posMin, p.posMax));
        int accPosition = accessors.size() - 1;

        int accNormal = -1;
        if (p.bvNormals >= 0) {
            accessors.add(makeAccessor(p.bvNormals, COMPONENT_TYPE_FLOAT, p.vertexCount,
                    "VEC3", null, null));
            accNormal = accessors.size() - 1;
        }

        int accTexCoord = -1;
        if (p.bvUvs >= 0) {
            accessors.add(makeAccessor(p.bvUvs, COMPONENT_TYPE_FLOAT, p.vertexCount,
                    "VEC2", null, null));
            accTexCoord = accessors.size() - 1;
        }

        int accColor = -1;
        if (p.bvColors >= 0) {
            accessors.add(makeAccessor(p.bvColors, COMPONENT_TYPE_FLOAT, p.vertexCount,
                    "VEC4", null, null));
            accColor = accessors.size() - 1;
        }

        accessors.add(makeAccessor(p.bvIndices, COMPONENT_TYPE_UNSIGNED_INT, p.vertexCount,
                "SCALAR", null, null));
        int accIndices = accessors.size() - 1;

        // _FEATURE_ID_0 is a vertex attribute, so it must use a glTF-core
        // vertex-attribute component type (FLOAT here — UNSIGNED_INT is
        // only legal on indices). Encoded values are still integral row
        // indices into the property table. Instanced prototype primitives
        // (bvFeatureIds < 0) carry no per-vertex ids.
        int accFeatureId = -1;
        if (p.bvFeatureIds >= 0) {
            accessors.add(makeAccessor(p.bvFeatureIds, COMPONENT_TYPE_FLOAT, p.vertexCount,
                    "SCALAR", null, null));
            accFeatureId = accessors.size() - 1;
        }

        // CESIUM_primitive_outline edge list: index pairs into the same
        // vertex arrays as the triangle indices, so UNSIGNED_INT is legal
        // here (it is an index accessor, not a vertex attribute).
        int accOutline = -1;
        if (p.bvOutlines >= 0) {
            accessors.add(makeAccessor(p.bvOutlines, COMPONENT_TYPE_UNSIGNED_INT,
                    p.outlineCount, "SCALAR", null, null));
            accOutline = accessors.size() - 1;
        }

        return new PrimitiveAccessors(accPosition, accNormal, accTexCoord, accColor,
                accIndices, accFeatureId, accOutline);
    }

    private void writeBufferViews(JSONObject root) {
        JSONArray bvArray = new JSONArray();
        for (GltfBufferView bv : bufferViews) {
            JSONObject bvObj = new JSONObject();
            bvObj.put("buffer", 0);
            bvObj.put("byteOffset", bv.byteOffset());
            bvObj.put("byteLength", bv.byteLength());
            if (bv.target() != GltfBufferView.TARGET_NONE) {
                bvObj.put("target", bv.target());
            }
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
     * Finish a glTF material from a (possibly populated) PBR block: apply the
     * project defaults (metallic 0, roughness 1, double-sided), optionally mark
     * {@code alphaMode=BLEND}, and — when {@code --enable-shading} is off — add
     * the unlit extension. The unlit extension matters because NORMAL-less
     * primitives would otherwise have CesiumJS auto-derive flat normals and dim
     * the surface; with shading on, the primitive carries normals and renders
     * PBR-shaded. Shared by the textured / plain / colored material paths.
     */
    private JSONObject finishMaterial(JSONObject pbr, boolean blend) {
        pbr.put("metallicFactor", 0.0);
        pbr.put("roughnessFactor", 1.0);
        JSONObject material = new JSONObject();
        material.put("pbrMetallicRoughness", pbr);
        if (blend) {
            material.put("alphaMode", "BLEND");
        }
        material.put("doubleSided", true);
        if (!enableShading) {
            addUnlitExtension(material);
        }
        return material;
    }

    /**
     * Emit one textured material per referenced atlas page (each with a
     * baseColorTexture pointing at its own atlas), one plain material per
     * distinct {@link DefaultObjectStyle}, at most one colored material, and
     * one textured material per distinct instanced prototype atlas page. All
     * material flavours follow {@code --enable-shading}:
     * <ul>
     *   <li><b>textured</b> — PBR-shaded under {@code --enable-shading};
     *       otherwise carries {@code KHR_materials_unlit}. With NORMAL on,
     *       the encoder emits the local up direction (constant +Y) per
     *       vertex so Lambertian gives every textured triangle in the node
     *       the same brightness — no per-face dimming on back-facing walls.
     *       Without NORMAL the unlit extension prevents CesiumJS from
     *       auto-deriving flat normals (which would dim the texture).
     *       Matches the I3S textured slot's NORMAL toggle.</li>
     *   <li><b>plain</b> — used by untextured primitives without X3DMaterial
     *       vertex colors. Renders PBR-shaded with NORMAL when
     *       {@code --enable-shading} is on; otherwise carries
     *       {@code KHR_materials_unlit} alongside the missing NORMAL so
     *       Cesium doesn't auto-derive flat normals. One material is
     *       emitted per distinct {@link DefaultObjectStyle} carried by the
     *       plain primitives in the node — this is how per-feature-type
     *       color overrides reach the GLB. Each material receives
     *       {@code baseColorFactor} from its style (omitted when the style
     *       still has the opaque-white default) and {@code alphaMode=BLEND}
     *       when the style's color carries alpha &lt; 1.</li>
     *   <li><b>colored</b> — used by untextured primitives carrying
     *       X3DMaterial {@code COLOR_0}. PBR-shaded under
     *       {@code --enable-shading}; otherwise unlit. Flipped to
     *       {@code alphaMode=BLEND} when any colored primitive has alpha
     *       below 1 — vertex {@code COLOR_0} multiplies into baseColor so
     *       BLEND is required for transparency to render.</li>
     * </ul>
     * All materials carry {@code doubleSided: true}. CityGML imports
     * routinely have inconsistent triangle winding on walls; the glTF default
     * (single-sided, back-face culled) would silently drop those triangles
     * from one viewing side. Mirrors the I3S writer's {@code cullFace="none"}
     * decision (see I3S {@code MaterialDefinition} javadoc).
     * Returns the resolved material indices so {@link #writeMeshes} can
     * reference them.
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
        List<Primitive> allPrimitives = new ArrayList<>(primitives);
        for (InstancedNode in : instancedNodes) {
            allPrimitives.addAll(in.primitives);
        }
        for (Primitive p : allPrimitives) {
            if (p.atlasPage >= 0) {
                pageUsed[p.atlasPage] = true;
            } else if (p.atlasPage == INSTANCED_TEXTURED_PAGE) {
                // Handled per instanced atlas below — one material per
                // distinct prototype atlas bufferView.
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
        LinkedHashSet<Integer> instancedAtlasBvs = distinctInstancedAtlasBvs();

        // Pass 2: emit materials in fixed order — textured (per atlas
        // page), plain (per distinct style), colored — and record indices.
        int textureSlot = 0;
        for (int p = 0; p < pageCount; p++) {
            if (!pageUsed[p]) continue;
            JSONObject pbr = new JSONObject();
            JSONObject baseColorTexture = new JSONObject();
            baseColorTexture.put("index", textureSlot++);
            baseColorTexture.put("texCoord", 0);
            pbr.put("baseColorTexture", baseColorTexture);
            texturedIdx[p] = materials.size();
            materials.add(finishMaterial(pbr, false));
        }

        Map<DefaultObjectStyle, Integer> plainIdxByStyle = new HashMap<>(plainStyles.size() * 2);
        for (DefaultObjectStyle style : plainStyles) {
            JSONObject pbr = new JSONObject();
            boolean blend = false;
            // baseColorFactor: only emit when the style is non-default
            // opaque white; the glTF default is opaque white so omitting
            // the field keeps the JSON minimal in the common case.
            if (style.hasNonDefaultColor()) {
                JSONArray baseColorFactor = new JSONArray();
                for (float v : style.toLinearRgba()) baseColorFactor.add(v);
                pbr.put("baseColorFactor", baseColorFactor);
                blend = style.hasAlpha();
            }
            plainIdxByStyle.put(style, materials.size());
            materials.add(finishMaterial(pbr, blend));
        }

        if (needColored) {
            // Colored primitives carry their colour in vertex COLOR_0, so the
            // PBR block keeps its defaults (no baseColorFactor).
            untexturedColoredIdx = materials.size();
            materials.add(finishMaterial(new JSONObject(), coloredNeedsBlend));
        }

        // One textured material per distinct instanced prototype atlas page;
        // their texture slots continue after the node atlas pages
        // (writeTextures appends the images in the same order).
        Map<Integer, Integer> instancedTexturedByBvAtlas = new HashMap<>();
        for (int bvAtlas : instancedAtlasBvs) {
            JSONObject pbr = new JSONObject();
            JSONObject baseColorTexture = new JSONObject();
            baseColorTexture.put("index", textureSlot++);
            baseColorTexture.put("texCoord", 0);
            pbr.put("baseColorTexture", baseColorTexture);
            instancedTexturedByBvAtlas.put(bvAtlas, materials.size());
            materials.add(finishMaterial(pbr, false));
        }

        root.put("materials", materials);
        return new MaterialIndices(texturedIdx, plainIdxByStyle, untexturedColoredIdx,
                instancedTexturedByBvAtlas);
    }

    private void addUnlitExtension(JSONObject material) {
        JSONObject extensions = new JSONObject();
        extensions.put("KHR_materials_unlit", new JSONObject());
        material.put("extensions", extensions);
        unlitUsed = true;
    }

    /**
     * Distinct instanced prototype atlas-page bufferViews in primitive
     * encounter order. The single source of the texture-slot sequence shared
     * by {@link #writeMaterials} (slot assignment, after the node atlas
     * pages) and {@link #writeTextures} (image/texture emission in the same
     * order).
     */
    private LinkedHashSet<Integer> distinctInstancedAtlasBvs() {
        LinkedHashSet<Integer> instancedAtlasBvs = new LinkedHashSet<>();
        for (InstancedNode in : instancedNodes) {
            for (Primitive p : in.primitives) {
                if (p.bvInstancedAtlas >= 0) {
                    instancedAtlasBvs.add(p.bvInstancedAtlas);
                }
            }
        }
        return instancedAtlasBvs;
    }

    private static void addImageTexture(JSONArray images, JSONArray textures, int bufferView) {
        JSONObject image = new JSONObject();
        image.put("bufferView", bufferView);
        image.put("mimeType", "image/jpeg");
        images.add(image);

        JSONObject texture = new JSONObject();
        texture.put("sampler", 0);
        texture.put("source", images.size() - 1);
        textures.add(texture);
    }

    /**
     * Emit one {@code images}/{@code textures} entry per referenced atlas
     * page, followed by one per distinct instanced prototype atlas, all
     * sharing a single sampler. Texture indices are assigned densely in this
     * order so they line up with the {@code textureSlot} counter used by
     * {@link #writeMaterials}.
     */
    private void writeTextures(JSONObject root) {
        JSONArray images = new JSONArray();
        JSONArray textures = new JSONArray();
        for (int bv : bvTextures) {
            if (bv < 0) continue;
            addImageTexture(images, textures, bv);
        }
        // Instanced prototype atlases follow the node atlas pages, in the
        // same distinctInstancedAtlasBvs() order writeMaterials used to
        // assign their texture slots.
        for (int bv : distinctInstancedAtlasBvs()) {
            addImageTexture(images, textures, bv);
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

    private void writeMeshes(JSONObject root, AccessorLayout accessors,
                             MaterialIndices materials) {
        JSONArray meshes = new JSONArray();

        if (!primitives.isEmpty()) {
            JSONArray primitivesArr = new JSONArray();
            for (int i = 0; i < primitives.size(); i++) {
                primitivesArr.add(writePrimitiveJson(primitives.get(i),
                        accessors.main.get(i), materials));
            }
            JSONObject mesh = new JSONObject();
            mesh.put("primitives", primitivesArr);
            meshes.add(mesh);
        }

        for (int n = 0; n < instancedNodes.size(); n++) {
            InstancedNode in = instancedNodes.get(n);
            List<PrimitiveAccessors> perPrim = accessors.instanced.get(n);
            JSONArray primitivesArr = new JSONArray();
            for (int i = 0; i < in.primitives.size(); i++) {
                primitivesArr.add(writePrimitiveJson(in.primitives.get(i),
                        perPrim.get(i), materials));
            }
            JSONObject mesh = new JSONObject();
            mesh.put("primitives", primitivesArr);
            meshes.add(mesh);
        }

        root.put("meshes", meshes);
    }

    /**
     * One glTF primitive object. Per-vertex {@code _FEATURE_ID_0} plus the
     * {@code EXT_mesh_features} declaration are emitted only when the
     * primitive carries a feature-id buffer — instanced prototype primitives
     * don't (their feature identity is per instance, declared on the node
     * via {@code EXT_instance_features}).
     */
    private JSONObject writePrimitiveJson(Primitive p, PrimitiveAccessors acc,
                                          MaterialIndices materials) {
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
        if (acc.featureId >= 0) {
            attributes.put("_FEATURE_ID_0", acc.featureId);
        }
        primitive.put("attributes", attributes);
        primitive.put("indices", acc.indices);
        int materialIdx;
        if (p.atlasPage >= 0) {
            materialIdx = materials.textured[p.atlasPage];
        } else if (p.atlasPage == INSTANCED_TEXTURED_PAGE) {
            materialIdx = materials.instancedTexturedByBvAtlas.get(p.bvInstancedAtlas);
        } else if (p.bvColors >= 0) {
            materialIdx = materials.untexturedColored;
        } else {
            materialIdx = materials.untexturedPlainByStyle.get(p.plainStyle);
        }
        primitive.put("material", materialIdx);

        JSONObject primExtensions = new JSONObject();
        if (acc.featureId >= 0) {
            JSONObject meshFeaturesExt = new JSONObject();
            JSONArray featureIdsArr = new JSONArray();
            JSONObject featureIdDef = new JSONObject();
            // Per the EXT_mesh_features spec, featureCount is the number of
            // unique features identified by THIS primitive's attribute — the
            // per-page/per-style split means each primitive references only a
            // subset of the node's property-table rows.
            featureIdDef.put("featureCount", p.featureCount);
            featureIdDef.put("attribute", 0);
            featureIdDef.put("propertyTable", 0);
            featureIdsArr.add(featureIdDef);
            meshFeaturesExt.put("featureIds", featureIdsArr);
            primExtensions.put("EXT_mesh_features", meshFeaturesExt);
        }
        if (acc.outline >= 0) {
            // CESIUM_primitive_outline: the referenced accessor holds pairs
            // of vertex indices, each pair one edge to render as an outline.
            JSONObject outlineExt = new JSONObject();
            outlineExt.put("indices", acc.outline);
            primExtensions.put("CESIUM_primitive_outline", outlineExt);
        }
        if (!primExtensions.isEmpty()) {
            primitive.put("extensions", primExtensions);
        }

        return primitive;
    }

    private void writeExtensions(JSONObject root) {
        JSONObject structMeta = buildStructuralMetadata();
        JSONObject rootExtensions = new JSONObject();
        rootExtensions.put("EXT_structural_metadata", structMeta);
        root.put("extensions", rootExtensions);

        JSONArray extUsed = new JSONArray();
        if (!primitives.isEmpty()) {
            extUsed.add("EXT_mesh_features");
        }
        extUsed.add("EXT_structural_metadata");
        if (!instancedNodes.isEmpty()) {
            extUsed.add("EXT_mesh_gpu_instancing");
            extUsed.add("EXT_instance_features");
            // EXT_mesh_gpu_instancing has no un-instanced fallback: a loader
            // ignoring it would render each prototype exactly once at the
            // node origin — silent data loss. The extension spec therefore
            // says files using it should list it in extensionsRequired so
            // non-supporting loaders fail cleanly instead. EXT_instance_features
            // stays optional: without it the geometry still renders correctly,
            // only per-instance picking degrades.
            JSONArray extRequired = new JSONArray();
            extRequired.add("EXT_mesh_gpu_instancing");
            root.put("extensionsRequired", extRequired);
        }
        if (unlitUsed) {
            extUsed.add("KHR_materials_unlit");
        }
        // CESIUM_primitive_outline goes into extensionsUsed only: a loader
        // that ignores it still renders the geometry correctly, the outlines
        // just don't draw — graceful degradation, unlike gpu_instancing.
        boolean outlineUsed = primitives.stream().anyMatch(p -> p.bvOutlines >= 0)
                || instancedNodes.stream()
                        .flatMap(n -> n.primitives().stream())
                        .anyMatch(p -> p.bvOutlines >= 0);
        if (outlineUsed) {
            extUsed.add("CESIUM_primitive_outline");
        }
        root.put("extensionsUsed", extUsed);
    }

    private JSONObject buildStructuralMetadata() {
        JSONObject meta = new JSONObject();

        JSONObject schema = new JSONObject();
        schema.put("id", MetadataSchema.SCHEMA_ID);
        JSONObject classes = new JSONObject();
        JSONObject featureClass = new JSONObject();
        JSONObject properties = new JSONObject();
        for (AttrField field : attrFields) {
            MetadataProperty mp = MetadataProperty.of(field.type());
            JSONObject prop = new JSONObject();
            prop.put("type", mp.type());
            if (mp.componentType() != null) {
                prop.put("componentType", mp.componentType());
            }
            properties.put(field.name(), prop);
        }
        featureClass.put("properties", properties);
        classes.put("feature", featureClass);
        schema.put("classes", classes);
        meta.put("schema", schema);

        JSONArray propTables = new JSONArray();
        JSONObject propTable = new JSONObject();
        propTable.put("class", "feature");
        propTable.put("count", propertyRowCount);

        JSONObject propTableProps = new JSONObject();
        for (int i = 0; i < attrFields.size(); i++) {
            AttrField field = attrFields.get(i);
            PropertyTableBufferViews pbv = propBvs.get(i);
            // Skip all-empty STRING columns — see PropertyTableBufferViews.SKIPPED.
            if (pbv.valuesBv() < 0) {
                continue;
            }
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
     * be {@code -1} when the primitive doesn't carry that attribute. NORMAL
     * is emitted on every path when {@code --enable-shading} is on and
     * dropped on every path otherwise — independent of the path. UV and
     * COLOR_0 are path-specific regardless of shading: textured primitives
     * carry UV (no color), X3DMaterial-colored primitives carry COLOR_0 (no
     * UV), and the untextured-plain path carries neither. {@code
     * anyAlphaBelowOne} flips the untextured colored material to
     * {@code alphaMode=BLEND}. {@code bvInstancedAtlas} is the atlas-page
     * bufferView an instanced textured primitive ({@code atlasPage ==}
     * {@link #INSTANCED_TEXTURED_PAGE}) samples; {@code -1} on every other
     * primitive flavour.
     * <p>
     * {@code bvOutlines}/{@code outlineCount}: bufferView and index count of
     * the CESIUM_primitive_outline edge list ({@code -1}/{@code 0} when the
     * primitive carries no outline). The count is carried explicitly because
     * the outline accessor is the only one whose element count differs from
     * {@code vertexCount}.
     */
    record Primitive(int atlasPage, int vertexCount, int featureCount,
                     float[] posMin, float[] posMax,
                     int bvPositions, int bvNormals, int bvUvs, int bvColors,
                     int bvIndices, int bvFeatureIds,
                     int bvInstancedAtlas,
                     int bvOutlines, int outlineCount,
                     boolean anyAlphaBelowOne,
                     DefaultObjectStyle plainStyle) {
    }

    /**
     * One GPU-instanced glTF node: the prototype's primitives (all with
     * {@code bvFeatureIds == -1}; textured ones carry their atlas page's
     * bufferView on {@code Primitive.bvInstancedAtlas}) plus the
     * {@code EXT_mesh_gpu_instancing} attribute buffer views.
     * {@code tMin}/{@code tMax} are the TRANSLATION accessor's per-axis
     * bounds.
     */
    record InstancedNode(List<Primitive> primitives, int instanceCount,
                         int bvTranslation, int bvRotation, int bvScale, int bvFeatureIds,
                         float[] tMin, float[] tMax) {
    }

    private record PrimitiveAccessors(int position, int normal, int texCoord, int color,
                                      int indices, int featureId, int outline) {
    }

    /**
     * Accessor indices produced by {@link #writeAccessors}: per main
     * primitive, per instanced-node primitive, and per instanced node the
     * TRS + instance-feature-id accessor quadruple
     * {@code [TRANSLATION, ROTATION, SCALE, _FEATURE_ID_0]}.
     */
    private record AccessorLayout(List<PrimitiveAccessors> main,
                                  List<List<PrimitiveAccessors>> instanced,
                                  List<int[]> instancedTrs) {
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
                                   int untexturedColored,
                                   Map<Integer, Integer> instancedTexturedByBvAtlas) {
    }
}
