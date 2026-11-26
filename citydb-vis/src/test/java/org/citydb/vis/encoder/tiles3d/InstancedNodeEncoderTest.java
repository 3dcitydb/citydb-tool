/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.encoder.tiles3d;

import org.citydb.core.json.Json;
import org.citydb.model.common.Name;
import org.citydb.vis.geometry.TriangleMesh;
import org.citydb.vis.model.FeatureData;
import org.citydb.vis.model.InstanceBatch;
import org.citydb.vis.model.InstancedFeature;
import org.citydb.vis.styling.ObjectStyleRegistry;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the multi-page prototype-atlas wiring of the instanced-node
 * encoder: a prototype whose textures were spilled onto two atlas pages must
 * emit one textured primitive per page, each bound to its own embedded atlas
 * bufferView and — at the glTF JSON level — its own material/texture. The
 * live-DB E2E scene only exercises single-page prototype atlases, so this is
 * the sole executable coverage of the {@code pageCount >= 2} routing.
 */
class InstancedNodeEncoderTest {
    private static final Name TYPE = Name.of("SolitaryVegetationObject");

    /**
     * A prototype mesh with two well-separated textured triangles referencing
     * distinct texture ids 0 and 1 (local Cartesian meters, valid normals and
     * UVs so welding keeps both).
     */
    private static TriangleMesh twoTexturePrototype() {
        TriangleMesh mesh = new TriangleMesh();
        int v0 = mesh.addVertex(0, 0, 0, 0, 0, 1, 0f, 0f);
        int v1 = mesh.addVertex(1, 0, 0, 0, 0, 1, 1f, 0f);
        int v2 = mesh.addVertex(0, 1, 0, 0, 0, 1, 0f, 1f);
        mesh.addTriangle(v0, v1, v2, 0L, 0, false, TYPE);
        int v3 = mesh.addVertex(0, 0, 5, 0, 0, 1, 0f, 0f);
        int v4 = mesh.addVertex(1, 0, 5, 0, 0, 1, 1f, 0f);
        int v5 = mesh.addVertex(0, 1, 5, 0, 0, 1, 0f, 1f);
        mesh.addTriangle(v3, v4, v5, 0L, 1, false, TYPE);
        return mesh;
    }

    @Test
    void multiPagePrototypeEmitsOnePrimitivePerPage() {
        InstancedFeature instance = new InstancedFeature(
                new double[]{0, 0, 0}, new float[]{0, 0, 0, 1}, new float[]{1, 1, 1},
                new float[]{1, 1, 1, 1},
                new FeatureData(1L, "tree_1", "SolitaryVegetationObject", Map.of()));
        InstanceBatch batch = new InstanceBatch(0, twoTexturePrototype(), 0.02f, 1.0,
                List.of(instance));
        // Two atlas pages, one texture each — the shape buildMulti produces
        // when the textures overflow --max-atlas-size.
        InstancedAtlas atlas = new InstancedAtlas(
                List.of(new byte[]{1, 2, 3}, new byte[]{4, 5, 6}),
                Map.of(0, 0, 1, 1));

        List<FeatureData> propFeatures = new ArrayList<>();
        List<InstancedNodeEncoder.InstancedNodeData> nodes = new InstancedNodeEncoder().build(
                List.of(batch), List.of(atlas), new double[]{0, 0, 0},
                ObjectStyleRegistry.empty(), false, false, propFeatures);

        assertEquals(1, nodes.size(), "fully textured prototype must form a single style group");
        assertEquals(1, propFeatures.size(), "one property-table row per instance");

        BinBufferBuilder bin = new BinBufferBuilder();
        List<GltfJsonBuilder.InstancedNode> instancedNodes =
                InstancedNodeEncoder.writeBuffers(nodes, bin);
        assertEquals(1, instancedNodes.size());

        List<GltfJsonBuilder.Primitive> prims = instancedNodes.get(0).primitives();
        assertEquals(2, prims.size(), "one textured primitive per atlas page");
        Set<Integer> atlasBvs = new HashSet<>();
        for (GltfJsonBuilder.Primitive p : prims) {
            assertEquals(GltfJsonBuilder.INSTANCED_TEXTURED_PAGE, p.atlasPage());
            assertTrue(p.bvInstancedAtlas() >= 0,
                    "instanced textured primitive must carry its page's atlas bufferView");
            atlasBvs.add(p.bvInstancedAtlas());
        }
        assertEquals(2, atlasBvs.size(), "each page primitive must bind a distinct atlas bufferView");

        // JSON level: two textured materials/images (one per page), and the
        // two primitives reference distinct materials.
        byte[] binData = bin.toByteArray();
        byte[] jsonBytes = new GltfJsonBuilder()
                .bufferViews(bin.getBufferViews(), binData.length)
                .instancedNodes(instancedNodes)
                .metadata(propFeatures.size(), List.of(), List.of())
                .enableShading(false)
                .build();
        JsonNode root = Json.readObject(new String(jsonBytes, StandardCharsets.UTF_8));

        assertEquals(2, root.get("materials").size());
        assertEquals(2, root.get("images").size());
        assertEquals(2, root.get("textures").size());
        JsonNode meshPrims = root.get("meshes").get(0)
                .get("primitives");
        assertEquals(2, meshPrims.size());
        assertNotEquals(meshPrims.get(0).path("material").asInt(0),
                meshPrims.get(1).path("material").asInt(0),
                "per-page primitives must not share a material");
        assertTrue(root.get("extensionsRequired").valueStream().anyMatch(n -> "EXT_mesh_gpu_instancing".equals(n.asString())));
    }

    /**
     * The prototype's normalization scale must be baked into the encoded
     * vertex positions with the matching division on each instance's SCALE
     * attribute: CesiumJS computes instanced culling bounds from POSITION
     * extents plus the TRANSLATION range, ignoring instance SCALE, so a
     * template encoded at unit size with SCALE=30 instances gets a bounding
     * sphere 30× too small and vanishes when the camera comes close.
     */
    @Test
    void normalizationScaleBakedIntoPositionsAndDividedOutOfInstanceScale() {
        TriangleMesh proto = new TriangleMesh();
        int v0 = proto.addVertex(0, 0, 0, 0, 0, 1, 0f, 0f);
        int v1 = proto.addVertex(1, 0, 0, 0, 0, 1, 1f, 0f);
        int v2 = proto.addVertex(0, 1, 0, 0, 0, 1, 0f, 1f);
        proto.addTriangle(v0, v1, v2, 0L, 0, false, TYPE);

        InstancedFeature instance = new InstancedFeature(
                new double[]{0, 0, 0}, new float[]{0, 0, 0, 1}, new float[]{30, 30, 30},
                new float[]{1, 1, 1, 1},
                new FeatureData(1L, "tree_1", "SolitaryVegetationObject", Map.of()));
        InstanceBatch batch = new InstanceBatch(0, proto, 0.02f, 30.0, List.of(instance));
        InstancedAtlas atlas = new InstancedAtlas(List.of(new byte[]{1, 2, 3}), Map.of(0, 0));

        List<FeatureData> propFeatures = new ArrayList<>();
        List<InstancedNodeEncoder.InstancedNodeData> nodes = new InstancedNodeEncoder().build(
                List.of(batch), List.of(atlas), new double[]{0, 0, 0},
                ObjectStyleRegistry.empty(), false, false, propFeatures);
        assertEquals(1, nodes.size());

        for (int k = 0; k < 3; k++) {
            assertEquals(1f, nodes.get(0).scales()[k], 1e-6f,
                    "instance SCALE must carry only the residual after normalization");
        }

        List<GltfJsonBuilder.InstancedNode> instancedNodes = InstancedNodeEncoder.writeBuffers(
                nodes, new BinBufferBuilder());
        GltfJsonBuilder.Primitive prim = instancedNodes.get(0).primitives().get(0);
        assertEquals(30f, prim.posMax()[0], 1e-4f,
                "prototype positions must be encoded at normalization scale");
    }

    /**
     * With outline emission enabled, an instanced prototype's primitives must
     * carry the CESIUM_primitive_outline edge list built from the template
     * mesh's outline masks — the outline is shared by all instances and
     * transformed together with the triangles by the GPU.
     */
    @Test
    void prototypeOutlineMasksReachInstancedPrimitives() {
        TriangleMesh proto = new TriangleMesh();
        int v0 = proto.addVertex(0, 0, 0, 0, 0, 1, 0f, 0f);
        int v1 = proto.addVertex(1, 0, 0, 0, 0, 1, 1f, 0f);
        int v2 = proto.addVertex(0, 1, 0, 0, 0, 1, 0f, 1f);
        proto.addTriangle(v0, v1, v2, 0L, 0, false, TYPE, TriangleMesh.OUTLINE_ALL_EDGES);

        InstancedFeature instance = new InstancedFeature(
                new double[]{0, 0, 0}, new float[]{0, 0, 0, 1}, new float[]{1, 1, 1},
                new float[]{1, 1, 1, 1},
                new FeatureData(1L, "tree_1", "SolitaryVegetationObject", Map.of()));
        InstanceBatch batch = new InstanceBatch(0, proto, 0.02f, 1.0, List.of(instance));
        InstancedAtlas atlas = new InstancedAtlas(List.of(new byte[]{1, 2, 3}), Map.of(0, 0));

        List<FeatureData> propFeatures = new ArrayList<>();
        List<InstancedNodeEncoder.InstancedNodeData> nodes = new InstancedNodeEncoder().build(
                List.of(batch), List.of(atlas), new double[]{0, 0, 0},
                ObjectStyleRegistry.empty(), false, true, propFeatures);
        assertEquals(1, nodes.size());

        BinBufferBuilder bin = new BinBufferBuilder();
        List<GltfJsonBuilder.InstancedNode> instancedNodes =
                InstancedNodeEncoder.writeBuffers(nodes, bin);
        GltfJsonBuilder.Primitive prim = instancedNodes.get(0).primitives().get(0);
        assertTrue(prim.bvOutlines() >= 0, "instanced primitive must carry an outline buffer");
        assertEquals(6, prim.outlineCount(), "three masked edges -> six indices");

        byte[] binData = bin.toByteArray();
        byte[] jsonBytes = new GltfJsonBuilder()
                .bufferViews(bin.getBufferViews(), binData.length)
                .instancedNodes(instancedNodes)
                .metadata(propFeatures.size(), List.of(), List.of())
                .enableShading(false)
                .build();
        JsonNode root = Json.readObject(new String(jsonBytes, StandardCharsets.UTF_8));
        assertTrue(root.get("extensionsUsed").valueStream().anyMatch(n -> "CESIUM_primitive_outline".equals(n.asString())),
                "outline on an instanced primitive must be declared in extensionsUsed");
        JsonNode primitive = root.get("meshes").get(0)
                .get("primitives").get(0);
        assertTrue(primitive.get("extensions")
                        .has("CESIUM_primitive_outline"),
                "instanced primitive JSON must reference the outline accessor");
    }
}
