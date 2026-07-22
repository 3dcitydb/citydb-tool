/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.encoder.tiles3d;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.citydb.model.common.Name;
import org.citydb.vis.encoder.TriangleRouter;
import org.citydb.vis.encoder.TriangleRouter.RoutedTriangle;
import org.citydb.vis.geometry.TriangleMesh;
import org.citydb.vis.geometry.VertexWelder;
import org.citydb.vis.styling.ObjectStyleRegistry;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies CESIUM_primitive_outline emission end to end through the primitive
 * builder and the glTF JSON builder: the per-triangle outline masks become an
 * index-pair edge list into the primitive's soup-ordered vertex array, exposed
 * as an UNSIGNED_INT accessor referenced from the primitive's
 * {@code extensions.CESIUM_primitive_outline.indices}, with the extension
 * declared in {@code extensionsUsed} (never {@code extensionsRequired}).
 */
class GlbPrimitiveOutlineTest {
    private static final int COMPONENT_TYPE_UNSIGNED_INT = 5125;
    private static final Name TYPE = Name.of("Building");

    /**
     * Two well-separated untextured triangles with distinct outline masks.
     * Output vertex layout is triangle soup: triangle 0 -> vertices 0..2,
     * triangle 1 -> vertices 3..5.
     */
    private static TriangleMesh twoTriangleMesh(byte mask0, byte mask1) {
        TriangleMesh mesh = new TriangleMesh();
        int v0 = mesh.addVertex(0, 0, 0, 0, 0, 1);
        int v1 = mesh.addVertex(1, 0, 0, 0, 0, 1);
        int v2 = mesh.addVertex(0, 1, 0, 0, 0, 1);
        mesh.addTriangle(v0, v1, v2, 1L, -1, false, TYPE, mask0);
        int v3 = mesh.addVertex(5, 0, 0, 0, 0, 1);
        int v4 = mesh.addVertex(6, 0, 0, 0, 0, 1);
        int v5 = mesh.addVertex(5, 1, 0, 0, 0, 1);
        mesh.addTriangle(v3, v4, v5, 1L, -1, false, TYPE, mask1);
        return mesh;
    }

    private static GlbPrimitiveBuilder.PrimitiveArrays buildPlain(TriangleMesh mesh,
                                                                  boolean emitOutlines) {
        VertexWelder.WeldResult weld = VertexWelder.weldAndFilter(mesh, 0.5, 0.5, 0);
        List<RoutedTriangle> routed = TriangleRouter.route(mesh, weld,
                ObjectStyleRegistry.empty());
        assertEquals(2, routed.size(), "both triangles must survive welding");
        return GlbPrimitiveBuilder.build(mesh, weld, routed,
                GlbPrimitiveBuilder.UNTEXTURED_PLAIN_PAGE, routed.get(0).style(),
                CellFrame.identity(), false, true, emitOutlines);
    }

    @Test
    void outlineIndicesListMaskedEdgesAsSoupIndexPairs() {
        TriangleMesh mesh = twoTriangleMesh(
                (byte) (TriangleMesh.OUTLINE_EDGE_01 | TriangleMesh.OUTLINE_EDGE_12),
                (byte) (TriangleMesh.OUTLINE_EDGE_12 | TriangleMesh.OUTLINE_EDGE_20));

        GlbPrimitiveBuilder.PrimitiveArrays arrays = buildPlain(mesh, true);

        // Triangle 0 (vertices 0..2): edges v0->v1, v1->v2.
        // Triangle 1 (vertices 3..5): edges v1->v2 = (4,5), v2->v0 = (5,3).
        assertNotNull(arrays.outlineIndices());
        assertArrayEquals(new int[]{0, 1, 1, 2, 4, 5, 5, 3}, arrays.outlineIndices());
    }

    @Test
    void outlineOmittedWhenDisabledOrMaskless() {
        TriangleMesh masked = twoTriangleMesh(TriangleMesh.OUTLINE_ALL_EDGES,
                TriangleMesh.OUTLINE_ALL_EDGES);
        assertNull(buildPlain(masked, false).outlineIndices(),
                "emitOutlines=false must not collect outline indices");

        TriangleMesh maskless = twoTriangleMesh((byte) 0, (byte) 0);
        assertNull(buildPlain(maskless, true).outlineIndices(),
                "an all-zero mask set must not produce an empty accessor");
    }

    @Test
    void jsonDeclaresOutlineExtensionAndAccessor() {
        TriangleMesh mesh = twoTriangleMesh(TriangleMesh.OUTLINE_ALL_EDGES, (byte) 0);
        GlbPrimitiveBuilder.PrimitiveArrays arrays = buildPlain(mesh, true);

        BinBufferBuilder bin = new BinBufferBuilder();
        GlbPrimitiveBuilder.PrimitiveBufferIds ids = GlbPrimitiveBuilder.writeBuffers(bin, arrays);
        assertTrue(ids.outlines() >= 0);
        GltfJsonBuilder.Primitive prim = GlbPrimitiveBuilder.toJsonPrimitive(arrays, ids, null, -1);
        assertEquals(6, prim.outlineCount());

        byte[] binData = bin.toByteArray();
        byte[] jsonBytes = new GltfJsonBuilder()
                .bufferViews(bin.getBufferViews(), binData.length)
                .primitives(List.of(prim))
                .metadata(1, List.of(), List.of())
                .enableShading(false)
                .build();
        JSONObject root = JSON.parseObject(new String(jsonBytes, StandardCharsets.UTF_8));

        assertTrue(root.getJSONArray("extensionsUsed").contains("CESIUM_primitive_outline"));
        JSONArray extRequired = root.getJSONArray("extensionsRequired");
        assertTrue(extRequired == null || !extRequired.contains("CESIUM_primitive_outline"),
                "outline degrades gracefully and must not be required");

        JSONObject primitive = root.getJSONArray("meshes").getJSONObject(0)
                .getJSONArray("primitives").getJSONObject(0);
        JSONObject outlineExt = primitive.getJSONObject("extensions")
                .getJSONObject("CESIUM_primitive_outline");
        assertNotNull(outlineExt, "primitive must carry the outline extension");

        JSONObject accessor = root.getJSONArray("accessors")
                .getJSONObject(outlineExt.getIntValue("indices"));
        assertEquals(COMPONENT_TYPE_UNSIGNED_INT, accessor.getIntValue("componentType"));
        assertEquals("SCALAR", accessor.getString("type"));
        assertEquals(6, accessor.getIntValue("count"),
                "three outlined edges of one triangle -> six indices");
    }

    @Test
    void jsonOmitsOutlineExtensionWhenNoPrimitiveCarriesOne() {
        TriangleMesh mesh = twoTriangleMesh(TriangleMesh.OUTLINE_ALL_EDGES, (byte) 0);
        GlbPrimitiveBuilder.PrimitiveArrays arrays = buildPlain(mesh, false);

        BinBufferBuilder bin = new BinBufferBuilder();
        GlbPrimitiveBuilder.PrimitiveBufferIds ids = GlbPrimitiveBuilder.writeBuffers(bin, arrays);
        GltfJsonBuilder.Primitive prim = GlbPrimitiveBuilder.toJsonPrimitive(arrays, ids, null, -1);

        byte[] binData = bin.toByteArray();
        byte[] jsonBytes = new GltfJsonBuilder()
                .bufferViews(bin.getBufferViews(), binData.length)
                .primitives(List.of(prim))
                .metadata(1, List.of(), List.of())
                .enableShading(false)
                .build();
        JSONObject root = JSON.parseObject(new String(jsonBytes, StandardCharsets.UTF_8));

        assertFalse(root.getJSONArray("extensionsUsed").contains("CESIUM_primitive_outline"));
    }
}
