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
import org.citydb.vis.model.InstanceBatch;
import org.citydb.vis.model.InstancedFeature;
import org.citydb.vis.scene.BoundingVolume;
import org.citydb.vis.scene.SceneNode;
import org.citydb.vis.styling.DefaultObjectStyle;
import org.citydb.vis.styling.ObjectStyleRegistry;
import org.citydb.vis.util.ColorUtils;
import org.citydb.vis.util.GeoTransform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

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
 *       "up" direction (+Y in GLB Y-up local frame, which the per-cell
 *       tile transform rotates to ECEF up at the cell center) — not the
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
 * Positions are in a local ENU coordinate frame relative to the node's cell
 * center. The cell root's per-cell {@code transform} in the tileset converts
 * that cell-local ENU to global ECEF, re-establishing the tangent plane at
 * every cell so Earth curvature does not lift distant cells off the ground.
 */
public class GlbEncoder {
    private static final Logger logger = LoggerFactory.getLogger(GlbEncoder.class);

    // Per-prototype welded/routed/array-built geometry, computed once per
    // export and shared across every GLB (and style group) referencing the
    // prototype. Keyed by mesh identity: prototype meshes are frozen after
    // PrototypeRegistry.buildAtlases, and the other build inputs are
    // per-prototype constants by construction (weld tolerance frozen by
    // buildAtlases, atlas bytes canonicalized by the writer's JPEG cache,
    // style registry and shading flag writer-lifetime). Concurrent: encode()
    // runs on the parallel node fan-out; populated via get/putIfAbsent so a
    // heavyweight build never runs under the map's bin lock — a racing
    // duplicate build is idempotent and the losers adopt the winner.
    private final Map<TriangleMesh, BatchGeometry> prototypeGeometryCache =
            new ConcurrentHashMap<>();

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
     * @param cellCenter     [centerLon, centerLat, centerAlt] of this node's
     *                       cell (the anchor of the cell root's per-cell
     *                       ENU-to-ECEF tile transform). All positions are
     *                       encoded relative to this point, so it must match
     *                       the anchor used to build the tile's transform.
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
                         List<InstanceBatch> instanceBatches,
                         List<byte[]> instanceAtlasBytes,
                         double[] cellCenter,
                         ObjectStyleRegistry styleRegistry,
                         boolean enableShading) throws IOException {
        TriangleMesh mesh = node.getMesh();
        boolean hasInstances = instanceBatches != null && !instanceBatches.isEmpty();
        boolean hasTexture = mesh.hasTexCoords() && !atlasBytesList.isEmpty();
        if (!hasTexture) {
            node.setTextured(false);
        }

        BoundingVolume mbs = node.getBoundingVolume();
        VertexWelder.WeldResult weld = VertexWelder.weldAndFilter(mesh,
                mbs.getCenterX(), mbs.getCenterY(), mbs.getCenterZ());
        node.setOutputVertexCount(weld.vertexCount());
        if (weld.isEmpty() && !hasInstances) {
            node.setMesh(null);
            return null;
        }

        CellFrame frame = CellFrame.from(mbs, cellCenter);

        int pageCount = atlasBytesList.size();
        List<PrimitiveArrays> primitives = new ArrayList<>();
        int featureCount = 0;
        if (!weld.isEmpty()) {
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

            featureCount = weld.faceRanges().size();

            // One primitive per non-empty atlas page + one plain primitive per
            // distinct DefaultObjectStyle in use + an optional colored primitive.
            // An empty page can arise when every triangle routed to it was dropped
            // by the weld/degenerate filter; its material + texture would still be
            // referenced in the glTF JSON, but no primitive would draw it, so we
            // simply skip it below and the JSON builder sees a compact set of
            // primitives.
            for (int p = 0; p < pageCount; p++) {
                List<RoutedTriangle> tris = texturedTrisByPage.get(p);
                if (!tris.isEmpty()) {
                    primitives.add(buildPrimitiveArrays(mesh, weld, tris, p, null, frame,
                            enableShading, true));
                }
            }
            for (Map.Entry<DefaultObjectStyle, List<RoutedTriangle>> e : untexturedPlainTrisByStyle.entrySet()) {
                if (!e.getValue().isEmpty()) {
                    primitives.add(buildPrimitiveArrays(mesh, weld, e.getValue(),
                            UNTEXTURED_PLAIN_PAGE, e.getKey(), frame, enableShading, true));
                }
            }
            if (!untexturedColoredTris.isEmpty()) {
                primitives.add(buildPrimitiveArrays(mesh, weld, untexturedColoredTris,
                        UNTEXTURED_COLORED_PAGE, null, frame, enableShading, true));
            }
        }
        node.setMesh(null); // release source mesh; no longer needed

        // Property-table rows: the mesh's face ranges first, then one row per
        // instance (appended by buildInstancedNodes in emission order, so the
        // per-instance _FEATURE_ID_0 values line up).
        List<FeatureData> propFeatures = featureCount < features.size()
                ? FeatureData.reorderByIds(features, weld.rangeFeatureIds())
                : features;
        List<InstancedNodeData> instancedNodes = List.of();
        if (hasInstances) {
            propFeatures = new ArrayList<>(propFeatures);
            instancedNodes = buildInstancedNodes(instanceBatches, instanceAtlasBytes,
                    cellCenter, styleRegistry, enableShading, propFeatures);
            if (primitives.isEmpty() && instancedNodes.isEmpty()) {
                return null;
            }
        }

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

        List<GltfJsonBuilder.InstancedNode> jsonInstancedNodes =
                new ArrayList<>(instancedNodes.size());
        // A batch's prototype geometry (and atlas JPEG) is shared by its
        // per-style node splits: write the buffer views once per distinct
        // BatchGeometry and let every split's primitives reference them —
        // only the material (style) differs per split.
        Map<BatchGeometry, BatchGeometryBuffers> geometryBuffers = new IdentityHashMap<>();
        for (InstancedNodeData ind : instancedNodes) {
            BatchGeometry geometry = ind.geometry;
            BatchGeometryBuffers buffers = geometryBuffers.get(geometry);
            if (buffers == null) {
                buffers = new BatchGeometryBuffers(
                        geometry.textured != null ? writePrimitiveBuffers(bin, geometry.textured) : null,
                        geometry.plain != null ? writePrimitiveBuffers(bin, geometry.plain) : null,
                        geometry.colored != null ? writePrimitiveBuffers(bin, geometry.colored) : null,
                        geometry.atlasBytes != null ? bin.addRawBytes(geometry.atlasBytes) : -1);
                geometryBuffers.put(geometry, buffers);
            }

            List<GltfJsonBuilder.Primitive> prims = new ArrayList<>(3);
            if (geometry.textured != null) {
                prims.add(toJsonPrimitive(geometry.textured, buffers.textured, null));
            }
            if (geometry.plain != null) {
                prims.add(toJsonPrimitive(geometry.plain, buffers.plain, ind.style));
            }
            if (geometry.colored != null) {
                prims.add(toJsonPrimitive(geometry.colored, buffers.colored, null));
            }
            jsonInstancedNodes.add(new GltfJsonBuilder.InstancedNode(
                    prims,
                    ind.instanceCount,
                    bin.addInstanceFloat32Array(ind.translations),
                    bin.addInstanceFloat32Array(ind.rotations),
                    bin.addInstanceFloat32Array(ind.scales),
                    bin.addInstanceFloat32Array(ind.featureIds),
                    buffers.bvAtlas,
                    ind.tMin, ind.tMax));
        }

        List<PropertyTableBufferViews> propBvs = new ArrayList<>();
        for (AttrField field : attrFields) {
            propBvs.add(encodePropertyField(bin, field, propFeatures));
        }

        byte[] binData = bin.toByteArray();
        byte[] jsonData = new GltfJsonBuilder()
                .bufferViews(bin.getBufferViews(), binData.length)
                .textures(bvTextures)
                .primitives(toJsonPrimitives(primitives, primitiveBvs))
                .instancedNodes(jsonInstancedNodes)
                .metadata(propFeatures.size(), attrFields, propBvs)
                .enableShading(enableShading)
                .build();

        return GlbContainer.assemble(jsonData, binData);
    }

    /**
     * Turn each prototype batch into one instanced-node payload per distinct
     * per-instance style. The style-independent prototype geometry (welded,
     * routed, and built into primitive arrays in the local meter frame — no
     * per-vertex feature ids, feature identity is per instance via
     * {@code EXT_instance_features}) is computed once per prototype for the
     * writer's lifetime and shared across GLBs and style groups; each group
     * only assembles its TRS + feature-id instance attribute arrays. Appends
     * one property-table row per instance to {@code propFeatures}; the
     * appended row index is the instance's {@code _FEATURE_ID_0} value.
     */
    private List<InstancedNodeData> buildInstancedNodes(
            List<InstanceBatch> instanceBatches, List<byte[]> instanceAtlasBytes,
            double[] cellCenter,
            ObjectStyleRegistry styleRegistry, boolean enableShading,
            List<FeatureData> propFeatures) {
        List<InstancedNodeData> out = new ArrayList<>();
        for (int b = 0; b < instanceBatches.size(); b++) {
            InstanceBatch batch = instanceBatches.get(b);
            byte[] atlasBytes = instanceAtlasBytes != null && b < instanceAtlasBytes.size()
                    ? instanceAtlasBytes.get(b) : null;
            BatchGeometry geometry = prototypeGeometryCache.get(batch.prototypeMesh());
            if (geometry == null) {
                geometry = buildBatchGeometry(batch, atlasBytes, styleRegistry, enableShading);
                BatchGeometry existing =
                        prototypeGeometryCache.putIfAbsent(batch.prototypeMesh(), geometry);
                if (existing != null) {
                    geometry = existing;
                }
            }
            if (geometry.isEmpty()) {
                // Warned at build time (racing builders may each warn once);
                // instances of this prototype are dropped in every node that
                // references it.
                continue;
            }

            // Group the batch's instances by their ingest-resolved style —
            // one glTF node (and one plain material) per distinct style.
            // DefaultObjectStyle is value-equal over the color, so it serves
            // as the map key directly. The style binds only to the plain
            // primitive, so a prototype without one (fully textured and/or
            // X3DMaterial-colored) keeps all instances in a single node.
            Map<DefaultObjectStyle, List<InstancedFeature>> byStyle = new LinkedHashMap<>();
            if (geometry.plain() == null) {
                byStyle.put(DefaultObjectStyle.defaults(), batch.instances());
            } else {
                for (InstancedFeature instance : batch.instances()) {
                    float[] c = instance.styleColor();
                    byStyle.computeIfAbsent(
                            DefaultObjectStyle.defaults().setColor(c[0], c[1], c[2], c[3]),
                            k -> new ArrayList<>()).add(instance);
                }
            }

            for (Map.Entry<DefaultObjectStyle, List<InstancedFeature>> group : byStyle.entrySet()) {
                List<InstancedFeature> instances = group.getValue();
                int count = instances.size();
                float[] translations = new float[count * 3];
                float[] rotations = new float[count * 4];
                float[] scales = new float[count * 3];
                float[] featureIds = new float[count];
                float[] tMin = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE};
                float[] tMax = {-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
                for (int i = 0; i < count; i++) {
                    InstancedFeature instance = instances.get(i);
                    InstanceTransformEncoder.InstanceTrs trs = InstanceTransformEncoder.encode(
                            instance.anchor(), instance.rotation(), instance.scale(), cellCenter);
                    for (int k = 0; k < 3; k++) {
                        translations[i * 3 + k] = trs.translation()[k];
                        scales[i * 3 + k] = trs.scale()[k];
                        tMin[k] = Math.min(tMin[k], trs.translation()[k]);
                        tMax[k] = Math.max(tMax[k], trs.translation()[k]);
                    }
                    for (int k = 0; k < 4; k++) {
                        rotations[i * 4 + k] = trs.rotation()[k];
                    }
                    featureIds[i] = propFeatures.size();
                    propFeatures.add(instance.feature());
                }
                out.add(new InstancedNodeData(geometry, group.getKey(), count,
                        translations, rotations, scales, featureIds, tMin, tMax));
            }
        }
        return out;
    }

    /**
     * Build a prototype's style-independent geometry once: weld in the local
     * meter frame at the batch's scale-adjusted tolerance, route triangles,
     * and bake the (up to three) primitive array sets. The plain primitive is
     * built without a style — the per-group style binds at material level in
     * {@code toJsonPrimitive}.
     */
    private static BatchGeometry buildBatchGeometry(InstanceBatch batch,
                                                    byte[] atlasBytes,
                                                    ObjectStyleRegistry styleRegistry,
                                                    boolean enableShading) {
        TriangleMesh proto = batch.prototypeMesh();
        VertexWelder.WeldResult weld = VertexWelder.weldAndFilter(proto, 0, 0, 0, true,
                batch.weldTolerance());
        if (weld.isEmpty()) {
            logger.warn("Implicit-geometry template {} welded to empty at tolerance {} — " +
                    "all of its instances are dropped from the 3D Tiles output.",
                    batch.prototypeId(), batch.weldTolerance());
            return BatchGeometry.EMPTY;
        }
        List<RoutedTriangle> routed = TriangleRouter.route(proto, weld, styleRegistry);
        List<RoutedTriangle> texturedTris = new ArrayList<>();
        List<RoutedTriangle> plainTris = new ArrayList<>();
        List<RoutedTriangle> coloredTris = new ArrayList<>();
        for (RoutedTriangle rt : routed) {
            // Textured triangles sample the prototype's own single-page
            // atlas (UVs already remapped at atlas-build time). Without an
            // atlas (untextured prototype, or every source texture failed)
            // they degrade to the plain bucket so the shape still renders.
            if (rt.textured() && atlasBytes != null) {
                texturedTris.add(rt);
            } else if (rt.colored()) {
                coloredTris.add(rt);
            } else {
                plainTris.add(rt);
            }
        }

        CellFrame identity = CellFrame.identity();
        PrimitiveArrays textured = texturedTris.isEmpty() ? null
                : buildPrimitiveArrays(proto, weld, texturedTris,
                        GltfJsonBuilder.INSTANCED_TEXTURED_PAGE, null, identity,
                        enableShading, false);
        PrimitiveArrays plain = plainTris.isEmpty() ? null
                : buildPrimitiveArrays(proto, weld, plainTris,
                        UNTEXTURED_PLAIN_PAGE, null, identity, enableShading, false);
        PrimitiveArrays colored = coloredTris.isEmpty() ? null
                : buildPrimitiveArrays(proto, weld, coloredTris,
                        UNTEXTURED_COLORED_PAGE, null, identity, enableShading, false);
        return new BatchGeometry(textured, plain, colored,
                textured != null ? atlasBytes : null);
    }

    /**
     * A prototype's style-independent primitive array sets (each {@code null}
     * when the prototype has no triangles of that flavour) plus its atlas
     * JPEG ({@code null} when untextured). Shared across GLBs and style
     * groups; identity is what {@code geometryBuffers} dedups on.
     */
    private record BatchGeometry(PrimitiveArrays textured, PrimitiveArrays plain,
                                 PrimitiveArrays colored, byte[] atlasBytes) {
        static final BatchGeometry EMPTY = new BatchGeometry(null, null, null, null);

        boolean isEmpty() {
            return textured == null && plain == null && colored == null;
        }
    }

    /** Buffer view ids of one {@link BatchGeometry}, written once per GLB. */
    private record BatchGeometryBuffers(PrimitiveBufferIds textured, PrimitiveBufferIds plain,
                                        PrimitiveBufferIds colored, int bvAtlas) {
    }

    /**
     * Assembled per-(prototype, style) instanced-node payload: the shared
     * prototype geometry, the group's plain-material style, and the group's
     * instance attribute arrays.
     */
    private record InstancedNodeData(BatchGeometry geometry, DefaultObjectStyle style,
                                     int instanceCount,
                                     float[] translations, float[] rotations, float[] scales,
                                     float[] featureIds, float[] tMin, float[] tMax) {
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
        // than enough for property-table row indices. Instanced prototype
        // primitives carry no per-vertex ids (featureIds == null) — their
        // feature identity is per instance via EXT_instance_features.
        int bvFeatureIds = -1;
        if (p.featureIds != null) {
            float[] featureIdsFloat = new float[p.featureIds.length];
            for (int i = 0; i < p.featureIds.length; i++) featureIdsFloat[i] = p.featureIds[i];
            bvFeatureIds = bin.addFloat32Array(featureIdsFloat);
        }
        return new PrimitiveBufferIds(bvPositions, bvNormals, bvUvs, bvColors,
                bvIndices, bvFeatureIds);
    }

    private static List<GltfJsonBuilder.Primitive> toJsonPrimitives(
            List<PrimitiveArrays> primitives, List<PrimitiveBufferIds> bvs) {
        List<GltfJsonBuilder.Primitive> out = new ArrayList<>(primitives.size());
        for (int i = 0; i < primitives.size(); i++) {
            out.add(toJsonPrimitive(primitives.get(i), bvs.get(i), null));
        }
        return out;
    }

    /**
     * Bind one primitive's arrays and buffer views into the JSON-builder
     * shape. {@code styleOverride} lets instanced style groups share one
     * style-independent plain geometry set while binding their own material;
     * {@code null} keeps the style baked into the arrays (main mesh path).
     */
    private static GltfJsonBuilder.Primitive toJsonPrimitive(
            PrimitiveArrays p, PrimitiveBufferIds b, DefaultObjectStyle styleOverride) {
        return new GltfJsonBuilder.Primitive(
                p.atlasPage,
                p.vertexCount, p.uniqueFeatureCount, p.posMin, p.posMax,
                b.positions, b.normals, b.uvs, b.colors, b.indices, b.featureIds,
                p.anyAlphaBelowOne, styleOverride != null ? styleOverride : p.plainStyle);
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
     */
    private static PrimitiveArrays buildPrimitiveArrays(TriangleMesh mesh,
                                                        VertexWelder.WeldResult weld,
                                                        List<RoutedTriangle> triEntries,
                                                        int atlasPage,
                                                        DefaultObjectStyle plainStyle,
                                                        CellFrame frame,
                                                        boolean enableShading,
                                                        boolean perVertexFeatureIds) {
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

        return new PrimitiveArrays(atlasPage, vertexCount, positions, normals, uvs,
                colors, indices, featureIds,
                uniqueFeatures != null ? uniqueFeatures.size() : 0,
                posMin, posMax, anyAlphaBelowOne, plainStyle);
    }

    /**
     * Scale/offset from node-local meters to cell-centered ENU meters.
     * Welded positions are relative to the node center; the offset shifts them
     * into the cell-centered frame that the cell root's per-cell ENU-to-ECEF
     * tile transform maps into global ECEF. Because the anchor is the cell
     * center (not the whole dataset), positions stay small (bounded by the
     * cell extent) and the tangent plane is re-established at every cell, so
     * Earth curvature no longer lifts far-from-center cells off the ground.
     */
    private record CellFrame(double scaleX, double scaleY,
                             float offsetX, float offsetY, float offsetZ) {
        /**
         * Identity frame for instanced prototype meshes: their welded
         * positions are already local Cartesian meters centered on the
         * template origin, so no degree scaling and no cell offset apply —
         * placement happens per instance via the TRS attributes.
         */
        static CellFrame identity() {
            return new CellFrame(1.0, 1.0, 0f, 0f, 0f);
        }

        static CellFrame from(BoundingVolume mbs, double[] cellCenter) {
            double scaleX = GeoTransform.metersPerDegreeLon(cellCenter[1]);
            double scaleY = GeoTransform.WGS84_METERS_PER_DEGREE_LAT;
            float offsetX = (float) ((mbs.getCenterX() - cellCenter[0]) * scaleX);
            float offsetY = (float) ((mbs.getCenterY() - cellCenter[1]) * scaleY);
            float offsetZ = (float) (mbs.getCenterZ() - cellCenter[2]);
            return new CellFrame(scaleX, scaleY, offsetX, offsetY, offsetZ);
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
    // The instanced-textured sentinel is owned by GltfJsonBuilder (which does
    // the material routing on it); this class references it directly.

    private record PrimitiveArrays(int atlasPage, int vertexCount,
                                   float[] positions, float[] normals, float[] uvs,
                                   float[] colors,
                                   int[] indices, int[] featureIds,
                                   int uniqueFeatureCount,
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
