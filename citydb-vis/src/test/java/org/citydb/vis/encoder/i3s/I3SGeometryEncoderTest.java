/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences (HFT Stuttgart) <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.encoder.i3s;

import org.citydb.model.common.Name;
import org.citydb.vis.geometry.TriangleMesh;
import org.citydb.vis.scene.BoundingVolume;
import org.citydb.vis.scene.SceneNode;
import org.citydb.vis.styling.ObjectStyleRegistry;
import org.citydb.vis.util.GeoTransform;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the uncompressed legacy I3S 1.9 geometry buffer that both ArcGIS and
 * CesiumJS parse positionally — any lane added, dropped, or reordered breaks
 * consumers with no error at export time:
 * <pre>
 *   UInt32 LE vertexCount, featureCount
 *   Float32 LE ×3 ×V positions (X/Y degree-offset from MBS center, Z meters)
 *   Float32 LE ×3 ×V normals   (only with --enable-shading)
 *   Float32 LE ×2 ×V uv0       (always declared; zero-padded when untextured)
 *   UInt8      ×4 ×V color     (only when the slot schema carries COLOR_0)
 *   UInt64 LE     ×F featureIds
 *   UInt32 LE ×2 ×F faceRanges (inclusive triangle ranges)
 * </pre>
 * Also guards the textured-slot up-normal trick and the exact-size lock-step
 * between emitted lanes and the declared slot schema.
 */
class I3SGeometryEncoderTest {
    private static final Name SURFACE = Name.of("WallSurface", "ns");
    // Node bbox: center (8.101, 48.701, 15) — all expected offsets below are
    // relative to this center.
    private static final BoundingVolume BBOX =
            BoundingVolume.ofBoundingBox(8.100, 48.700, 10.0, 8.102, 48.702, 20.0);

    @Test
    void untexturedBufferLayoutWithoutShadingOrColors(@TempDir Path tempDir) throws IOException {
        TriangleMesh mesh = twoFeatureMesh();
        SceneNode node = node(mesh);

        I3SGeometryEncoder.NodeGeometryResult result = new I3SGeometryEncoder()
                .writeNodeGeometry(tempDir, node, false, false, ObjectStyleRegistry.empty());

        ByteBuffer buf = read(tempDir, 2);
        // position(72) + uv0(48) lanes only: 8 + 6*12 + 6*8 + 2*8 + 2*8.
        assertEquals(160, buf.remaining());
        assertEquals(6, buf.getInt());
        assertEquals(2, buf.getInt());

        // Positions are center-relative degree/meter offsets in corner order.
        double[][] expected = {
                {8.100, 48.700, 10.0}, {8.101, 48.700, 10.0}, {8.100, 48.701, 12.0},
                {8.102, 48.702, 20.0}, {8.1015, 48.702, 20.0}, {8.102, 48.7015, 18.0}};
        for (double[] p : expected) {
            assertEquals((float) (p[0] - 8.101), buf.getFloat(), 1e-9, "position X offset");
            assertEquals((float) (p[1] - 48.701), buf.getFloat(), 1e-9, "position Y offset");
            assertEquals((float) (p[2] - 15.0), buf.getFloat(), 1e-6, "position Z offset");
        }

        // uv0 lane is declared on every slot; untextured vertices are zero.
        for (int i = 0; i < 6 * 2; i++) {
            assertEquals(0f, buf.getFloat(), "uv0 must be zero-padded");
        }

        assertEquals(1L, buf.getLong());
        assertEquals(2L, buf.getLong());
        // Inclusive per-feature triangle ranges.
        assertEquals(0, buf.getInt());
        assertEquals(0, buf.getInt());
        assertEquals(1, buf.getInt());
        assertEquals(1, buf.getInt());
        assertFalse(buf.hasRemaining());

        // Result aligns with the featureId/faceRange stream; AABBs are
        // absolute coordinates per feature (the JS SDK pick-BVH input).
        assertEquals(List.of(1L, 2L), result.rangeFeatureIds());
        double[] aabb = result.featureAabbs().get(0);
        assertEquals(8.100, aabb[0], 1e-6);
        assertEquals(48.700, aabb[1], 1e-6);
        assertEquals(10.0, aabb[2], 1e-4);
        assertEquals(8.101, aabb[3], 1e-6);
        assertEquals(48.701, aabb[4], 1e-6);
        assertEquals(12.0, aabb[5], 1e-4);

        // The mesh is released and the output vertex count recorded.
        assertNull(node.getMesh());
        assertEquals(6, node.getOutputVertexCount());
    }

    @Test
    void enableShadingEmitsEcefRotatedNormalLane(@TempDir Path tempDir) throws IOException {
        TriangleMesh mesh = twoFeatureMesh();
        SceneNode node = node(mesh);

        new I3SGeometryEncoder().writeNodeGeometry(tempDir, node, false, true,
                ObjectStyleRegistry.empty());

        ByteBuffer buf = read(tempDir, 2);
        assertEquals(160 + 6 * 12, buf.remaining());
        buf.position(8 + 6 * 12);

        // Untextured vertices carry their authored ENU normal rotated into
        // ECEF at the node center. Feature 1 authored up, feature 2 east.
        GeoTransform.EnuBasis enu = GeoTransform.EnuBasis.at(8.101, 48.701);
        float[] up = new float[3];
        enu.rotateNormalToEcef(new float[]{0, 0, 1}, up);
        float[] east = new float[3];
        enu.rotateNormalToEcef(new float[]{1, 0, 0}, east);
        for (int v = 0; v < 3; v++) {
            assertNormal(buf, up);
        }
        for (int v = 0; v < 3; v++) {
            assertNormal(buf, east);
        }
    }

    @Test
    void texturedVerticesGetUpNormalRegardlessOfAuthoredNormal(@TempDir Path tempDir)
            throws IOException {
        // Authored normal is EAST — the textured slot must replace it with the
        // local ENU up direction so all textured faces are evenly lit.
        TriangleMesh mesh = new TriangleMesh();
        int v0 = mesh.addVertex(8.100, 48.700, 10.0, 1, 0, 0, 0.1f, 0.2f);
        int v1 = mesh.addVertex(8.101, 48.700, 10.0, 1, 0, 0, 0.9f, 0.2f);
        int v2 = mesh.addVertex(8.100, 48.701, 12.0, 1, 0, 0, 0.1f, 0.8f);
        mesh.addTriangle(v0, v1, v2, 1L, 0, false, SURFACE);
        SceneNode node = node(mesh);

        new I3SGeometryEncoder().writeNodeGeometry(tempDir, node, false, true,
                ObjectStyleRegistry.empty());

        ByteBuffer buf = read(tempDir, 2);
        // 8 + positions(36) + normals(36) + uv0(24) + featureId(8) + faceRange(8).
        assertEquals(120, buf.remaining());
        buf.position(8 + 3 * 12);

        float[] up = new float[3];
        GeoTransform.EnuBasis.at(8.101, 48.701).fillUpInEcef(up);
        for (int v = 0; v < 3; v++) {
            assertNormal(buf, up);
        }

        // Authored UVs pass through unchanged.
        assertEquals(0.1f, buf.getFloat());
        assertEquals(0.2f, buf.getFloat());
        assertEquals(0.9f, buf.getFloat());
        assertEquals(0.2f, buf.getFloat());
        assertEquals(0.1f, buf.getFloat());
        assertEquals(0.8f, buf.getFloat());
    }

    @Test
    void x3dColoredNodeBakesAuthoredRgbaAndFlagsBlend(@TempDir Path tempDir) throws IOException {
        // No UVs + baked X3DMaterial vertex colors → the colored slot with a
        // COLOR_0 lane carrying the authored RGBA as bytes.
        TriangleMesh mesh = new TriangleMesh();
        int v0 = mesh.addVertex(8.100, 48.700, 10.0, 0, 0, 1, 1f, 0f, 0f, 0.5f);
        int v1 = mesh.addVertex(8.101, 48.700, 10.0, 0, 0, 1, 1f, 0f, 0f, 0.5f);
        int v2 = mesh.addVertex(8.100, 48.701, 12.0, 0, 0, 1, 1f, 0f, 0f, 0.5f);
        mesh.addTriangle(v0, v1, v2, 1L, -1, true, SURFACE);
        SceneNode node = node(mesh);

        new I3SGeometryEncoder().writeNodeGeometry(tempDir, node, true, false,
                ObjectStyleRegistry.empty());

        ByteBuffer buf = read(tempDir, 2);
        // 8 + positions(36) + uv0(24) + color(12) + featureId(8) + faceRange(8).
        assertEquals(96, buf.remaining());
        buf.position(8 + 3 * 12 + 3 * 8);
        for (int v = 0; v < 3; v++) {
            assertEquals((byte) 255, buf.get(), "R");
            assertEquals((byte) 0, buf.get(), "G");
            assertEquals((byte) 0, buf.get(), "B");
            assertEquals((byte) 128, buf.get(), "A = round(0.5*255)");
        }

        assertTrue(node.isColored());
        assertTrue(node.isColoredBlend(), "alpha < 1 must promote the material to BLEND");
    }

    @Test
    void plainNodeOnColoredLayerOmitsColorLane(@TempDir Path tempDir) throws IOException {
        // Colored layer, but this node has neither texture nor X3D colors:
        // the untextured slot declares no COLOR_0, so none may be emitted —
        // lane presence must stay in lock-step with the slot schema.
        TriangleMesh mesh = twoFeatureMesh();
        SceneNode node = node(mesh);

        new I3SGeometryEncoder().writeNodeGeometry(tempDir, node, true, false,
                ObjectStyleRegistry.empty());

        assertEquals(160, read(tempDir, 2).remaining(), "no COLOR_0 lane expected");
        assertFalse(node.isColored());
    }

    @Test
    void meshLeftEmptyByDegenerateFilteringReturnsNull(@TempDir Path tempDir) throws IOException {
        // A single zero-area triangle is welded/filtered away entirely; the
        // node must be reported as non-mesh and carry no geometry file.
        TriangleMesh mesh = new TriangleMesh();
        int v0 = mesh.addVertex(8.100, 48.700, 10.0, 0, 0, 1);
        int v1 = mesh.addVertex(8.100, 48.700, 10.0, 0, 0, 1);
        int v2 = mesh.addVertex(8.100, 48.700, 10.0, 0, 0, 1);
        mesh.addTriangle(v0, v1, v2, 1L, -1, false, SURFACE);
        SceneNode node = node(mesh);

        assertNull(new I3SGeometryEncoder().writeNodeGeometry(tempDir, node, false, false,
                ObjectStyleRegistry.empty()));
        assertNull(node.getMesh());
        assertFalse(Files.exists(
                tempDir.resolve("nodes").resolve("2").resolve("geometries").resolve("0")));
    }

    // ---- fixture -----------------------------------------------------------

    /** Two single-triangle features: feature 1 authored up, feature 2 east. */
    private static TriangleMesh twoFeatureMesh() {
        TriangleMesh mesh = new TriangleMesh();
        int v0 = mesh.addVertex(8.100, 48.700, 10.0, 0, 0, 1);
        int v1 = mesh.addVertex(8.101, 48.700, 10.0, 0, 0, 1);
        int v2 = mesh.addVertex(8.100, 48.701, 12.0, 0, 0, 1);
        mesh.addTriangle(v0, v1, v2, 1L, -1, false, SURFACE);
        int v3 = mesh.addVertex(8.102, 48.702, 20.0, 1, 0, 0);
        int v4 = mesh.addVertex(8.1015, 48.702, 20.0, 1, 0, 0);
        int v5 = mesh.addVertex(8.102, 48.7015, 18.0, 1, 0, 0);
        mesh.addTriangle(v3, v4, v5, 2L, -1, false, SURFACE);
        return mesh;
    }

    private static SceneNode node(TriangleMesh mesh) {
        return new SceneNode(2, 1)
                .setBoundingVolume(BBOX)
                .setMesh(mesh);
    }

    private static ByteBuffer read(Path layerDir, int nodeIndex) throws IOException {
        byte[] bytes = Files.readAllBytes(layerDir.resolve("nodes")
                .resolve(String.valueOf(nodeIndex)).resolve("geometries").resolve("0"));
        return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
    }

    private static void assertNormal(ByteBuffer buf, float[] expected) {
        assertEquals(expected[0], buf.getFloat(), 1e-6, "normal X");
        assertEquals(expected[1], buf.getFloat(), 1e-6, "normal Y");
        assertEquals(expected[2], buf.getFloat(), 1e-6, "normal Z");
    }
}
