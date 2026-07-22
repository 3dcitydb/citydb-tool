/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.encoder.tiles3d;

import org.citydb.vis.encoder.TriangleRouter;
import org.citydb.vis.encoder.TriangleRouter.RoutedTriangle;
import org.citydb.vis.geometry.TriangleMesh;
import org.citydb.vis.geometry.VertexWelder;
import org.citydb.vis.model.FeatureData;
import org.citydb.vis.model.InstanceBatch;
import org.citydb.vis.model.InstancedFeature;
import org.citydb.vis.styling.DefaultObjectStyle;
import org.citydb.vis.styling.ObjectStyleRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Encodes GPU-instanced implicit geometry ({@code EXT_mesh_gpu_instancing})
 * for the GLB path: turns each prototype batch into per-(prototype × style)
 * instanced-node payloads ({@link #build}) and writes their buffer views
 * ({@link #writeBuffers}). The style-independent prototype geometry (welded,
 * routed, and baked into primitive arrays in the local meter frame) is
 * computed once per prototype and cached for the encoder's lifetime, shared
 * across every GLB and style group referencing the prototype.
 * <p>
 * A textured prototype carries one textured primitive per atlas page (usually
 * one; several when its textures overflow {@code --max-atlas-size} and the
 * atlas spilled to multiple pages), mirroring the main mesh's per-page
 * primitive split.
 * <p>
 * Package-private companion of {@link GlbEncoder}, which owns the surrounding
 * node encode flow; I3S has no instancing concept and bakes instances instead.
 */
final class InstancedNodeEncoder {
    private static final Logger logger = LoggerFactory.getLogger(InstancedNodeEncoder.class);

    // Per-prototype welded/routed/array-built geometry, computed once per
    // export and shared across every GLB (and style group) referencing the
    // prototype. Keyed by mesh identity: prototype meshes are frozen after
    // PrototypeRegistry.buildAtlases, and the other build inputs are
    // per-prototype constants by construction (weld tolerance frozen by
    // buildAtlases, atlas page bytes and texId→page map canonicalized by the
    // writer's per-page JPEG cache, style registry and the shading/outline
    // flags writer-lifetime). Concurrent: encoding runs on the parallel node
    // fan-out; populated via get/putIfAbsent so a heavyweight build never
    // runs under the map's bin lock — a racing duplicate build is idempotent
    // and the losers adopt the winner.
    private final Map<TriangleMesh, BatchGeometry> prototypeGeometryCache =
            new ConcurrentHashMap<>();

    /**
     * A prototype's style-independent primitive array sets plus its atlas
     * pages. {@code texturedPages} holds one primitive per atlas page that
     * received at least one routed triangle, index-aligned with
     * {@code pageBytes} (the page's JPEG). {@code plain}/{@code colored} are
     * {@code null} when the prototype has no triangles of that flavour.
     * Shared across GLBs and style groups; identity is what
     * {@link #writeBuffers}'s dedup map keys on.
     */
    private record BatchGeometry(List<GlbPrimitiveBuilder.PrimitiveArrays> texturedPages,
                                 List<byte[]> pageBytes,
                                 GlbPrimitiveBuilder.PrimitiveArrays plain,
                                 GlbPrimitiveBuilder.PrimitiveArrays colored) {
        static final BatchGeometry EMPTY = new BatchGeometry(List.of(), List.of(), null, null);

        boolean isEmpty() {
            return texturedPages.isEmpty() && plain == null && colored == null;
        }
    }

    /**
     * Buffer view ids of one {@link BatchGeometry}, written once per GLB:
     * per-page primitive buffers + atlas bufferViews (index-aligned with
     * {@code BatchGeometry.texturedPages}) plus the plain/colored buffers.
     */
    private record BatchGeometryBuffers(List<GlbPrimitiveBuilder.PrimitiveBufferIds> texturedPages,
                                        List<Integer> bvAtlasPages,
                                        GlbPrimitiveBuilder.PrimitiveBufferIds plain,
                                        GlbPrimitiveBuilder.PrimitiveBufferIds colored) {
    }

    /**
     * Assembled per-(prototype, style) instanced-node payload: the shared
     * prototype geometry, the group's plain-material style, and the group's
     * instance attribute arrays.
     */
    record InstancedNodeData(BatchGeometry geometry, DefaultObjectStyle style,
                             int instanceCount,
                             float[] translations, float[] rotations, float[] scales,
                             float[] featureIds, float[] tMin, float[] tMax) {
    }

    /**
     * Turn each prototype batch into one instanced-node payload per distinct
     * per-instance style. The style-independent prototype geometry (no
     * per-vertex feature ids, feature identity is per instance via
     * {@code EXT_instance_features}) comes from the prototype cache; each
     * group only assembles its TRS + feature-id instance attribute arrays.
     * Appends one property-table row per instance to {@code propFeatures};
     * the appended row index is the instance's {@code _FEATURE_ID_0} value.
     */
    List<InstancedNodeData> build(
            List<InstanceBatch> instanceBatches, List<InstancedAtlas> instanceAtlases,
            double[] cellCenter,
            ObjectStyleRegistry styleRegistry, boolean enableShading,
            boolean enableOutline,
            List<FeatureData> propFeatures) {
        List<InstancedNodeData> out = new ArrayList<>();
        for (int b = 0; b < instanceBatches.size(); b++) {
            InstanceBatch batch = instanceBatches.get(b);
            InstancedAtlas atlas = instanceAtlases.get(b);
            BatchGeometry geometry = prototypeGeometryCache.get(batch.prototypeMesh());
            if (geometry == null) {
                geometry = buildBatchGeometry(batch, atlas, styleRegistry, enableShading,
                        enableOutline);
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
     * Write the instanced nodes' buffer views into the BIN and bind them into
     * the JSON-builder shape. A batch's prototype geometry (and atlas page
     * JPEGs) is shared by its per-style node splits: the buffer views are
     * written once per distinct {@link BatchGeometry} and every split's
     * primitives reference them — only the material (style) differs per
     * split. Each textured primitive carries its page's atlas bufferView on
     * {@code Primitive.bvInstancedAtlas} for the JSON builder's material
     * routing.
     */
    static List<GltfJsonBuilder.InstancedNode> writeBuffers(
            List<InstancedNodeData> instancedNodes, BinBufferBuilder bin) {
        List<GltfJsonBuilder.InstancedNode> jsonInstancedNodes =
                new ArrayList<>(instancedNodes.size());
        Map<BatchGeometry, BatchGeometryBuffers> geometryBuffers = new IdentityHashMap<>();
        for (InstancedNodeData ind : instancedNodes) {
            BatchGeometry geometry = ind.geometry();
            BatchGeometryBuffers buffers = geometryBuffers.get(geometry);
            if (buffers == null) {
                List<GlbPrimitiveBuilder.PrimitiveBufferIds> texturedPages =
                        new ArrayList<>(geometry.texturedPages().size());
                List<Integer> bvAtlasPages = new ArrayList<>(geometry.texturedPages().size());
                for (int p = 0; p < geometry.texturedPages().size(); p++) {
                    texturedPages.add(GlbPrimitiveBuilder.writeBuffers(bin,
                            geometry.texturedPages().get(p)));
                    bvAtlasPages.add(bin.addRawBytes(geometry.pageBytes().get(p)));
                }
                buffers = new BatchGeometryBuffers(
                        texturedPages, bvAtlasPages,
                        geometry.plain() != null
                                ? GlbPrimitiveBuilder.writeBuffers(bin, geometry.plain()) : null,
                        geometry.colored() != null
                                ? GlbPrimitiveBuilder.writeBuffers(bin, geometry.colored()) : null);
                geometryBuffers.put(geometry, buffers);
            }

            List<GltfJsonBuilder.Primitive> prims =
                    new ArrayList<>(geometry.texturedPages().size() + 2);
            for (int p = 0; p < geometry.texturedPages().size(); p++) {
                prims.add(GlbPrimitiveBuilder.toJsonPrimitive(geometry.texturedPages().get(p),
                        buffers.texturedPages().get(p), null, buffers.bvAtlasPages().get(p)));
            }
            if (geometry.plain() != null) {
                prims.add(GlbPrimitiveBuilder.toJsonPrimitive(geometry.plain(),
                        buffers.plain(), ind.style(), -1));
            }
            if (geometry.colored() != null) {
                prims.add(GlbPrimitiveBuilder.toJsonPrimitive(geometry.colored(),
                        buffers.colored(), null, -1));
            }
            jsonInstancedNodes.add(new GltfJsonBuilder.InstancedNode(
                    prims,
                    ind.instanceCount(),
                    bin.addInstanceFloat32Array(ind.translations()),
                    bin.addInstanceFloat32Array(ind.rotations()),
                    bin.addInstanceFloat32Array(ind.scales()),
                    bin.addInstanceFloat32Array(ind.featureIds()),
                    ind.tMin(), ind.tMax()));
        }
        return jsonInstancedNodes;
    }

    /**
     * Build a prototype's style-independent geometry once: weld in the local
     * meter frame at the batch's scale-adjusted tolerance, route triangles,
     * and bake the primitive array sets — one textured primitive per atlas
     * page holding routed triangles, plus optional plain/colored primitives.
     * The plain primitive is built without a style — the per-group style
     * binds at material level in
     * {@link GlbPrimitiveBuilder#toJsonPrimitive}.
     */
    private static BatchGeometry buildBatchGeometry(InstanceBatch batch,
                                                    InstancedAtlas atlas,
                                                    ObjectStyleRegistry styleRegistry,
                                                    boolean enableShading,
                                                    boolean enableOutline) {
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
        int pageCount = atlas != null ? atlas.pageBytes().size() : 0;
        // Textured triangles sample the prototype's own atlas pages (UVs
        // already remapped at atlas-build time), routed per page like the
        // main mesh path.
        TriangleRouter.PageBuckets buckets = TriangleRouter.bucketByPage(routed, pageCount,
                atlas != null ? atlas.texIdToPage() : null);
        List<RoutedTriangle> plainTris = buckets.plain();
        List<RoutedTriangle> coloredTris = buckets.colored();

        CellFrame identity = CellFrame.identity();
        // Prototype outline: the template mesh is triangulated by the same
        // PolygonTriangulator as main meshes, so its boundary-edge masks are
        // present. The edge list is shared by all instances — the GPU
        // transforms edges together with the triangles.
        List<GlbPrimitiveBuilder.PrimitiveArrays> texturedPages = new ArrayList<>();
        List<byte[]> pageBytes = new ArrayList<>();
        for (int p = 0; p < pageCount; p++) {
            List<RoutedTriangle> tris = buckets.texturedByPage().get(p);
            if (tris.isEmpty()) {
                continue;
            }
            texturedPages.add(GlbPrimitiveBuilder.build(proto, weld, tris,
                    GltfJsonBuilder.INSTANCED_TEXTURED_PAGE, null, identity,
                    enableShading, false, enableOutline));
            pageBytes.add(atlas.pageBytes().get(p));
        }
        GlbPrimitiveBuilder.PrimitiveArrays plain = plainTris.isEmpty() ? null
                : GlbPrimitiveBuilder.build(proto, weld, plainTris,
                        GlbPrimitiveBuilder.UNTEXTURED_PLAIN_PAGE, null, identity,
                        enableShading, false, enableOutline);
        GlbPrimitiveBuilder.PrimitiveArrays colored = coloredTris.isEmpty() ? null
                : GlbPrimitiveBuilder.build(proto, weld, coloredTris,
                        GlbPrimitiveBuilder.UNTEXTURED_COLORED_PAGE, null, identity,
                        enableShading, false, enableOutline);
        return new BatchGeometry(texturedPages, pageBytes, plain, colored);
    }
}
