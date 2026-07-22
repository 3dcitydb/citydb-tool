/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.store;

import org.citydb.model.common.Name;
import org.citydb.vis.geometry.TriangleMesh;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Round-trip test for the MeshStore binary format: every vertex lane and every
 * per-triangle attribute lane (featureId / textureId / colored / surfaceType /
 * outlineEdges) must survive serialize + deserialize unchanged. Guards the
 * write/read symmetry that a new lane block can silently break.
 */
class MeshStoreTest {

    private static final Name ROOF = Name.of("RoofSurface", "ns");
    private static final Name WALL = Name.of("WallSurface", "ns");

    @Test
    void roundTripPreservesAllTriangleLanes(@TempDir Path tempDir) throws IOException {
        TriangleMesh mesh = new TriangleMesh();
        // Textured triangle (UV lane) with a full outline mask.
        int v0 = mesh.addVertex(8.1, 48.7, 10.0, 0, 0, 1, 0.1f, 0.2f);
        int v1 = mesh.addVertex(8.2, 48.7, 10.0, 0, 0, 1, 0.9f, 0.2f);
        int v2 = mesh.addVertex(8.1, 48.8, 10.0, 0, 0, 1, 0.1f, 0.8f);
        mesh.addTriangle(v0, v1, v2, 100L, 3, false, ROOF,
                TriangleMesh.OUTLINE_ALL_EDGES);
        // Colored triangle (COLOR_0 lane) with a partial outline mask.
        int v3 = mesh.addVertex(8.3, 48.7, 0.0, 0, 0, 1, 1f, 0f, 0f, 0.5f);
        int v4 = mesh.addVertex(8.4, 48.7, 0.0, 0, 0, 1, 1f, 0f, 0f, 0.5f);
        int v5 = mesh.addVertex(8.3, 48.8, 0.0, 0, 0, 1, 1f, 0f, 0f, 0.5f);
        mesh.addTriangle(v3, v4, v5, 200L, -1, true, WALL,
                TriangleMesh.OUTLINE_EDGE_12);
        // Plain triangle reusing vertices, empty outline mask.
        mesh.addTriangle(v0, v2, v4, 300L, -1, false, WALL, (byte) 0);

        TriangleMesh loaded;
        try (MeshStore store = new MeshStore(tempDir)) {
            long handle = store.store(mesh);
            assertTrue(store.hasColors());
            loaded = store.load(handle);
        }

        assertEquals(mesh.getVertexCount(), loaded.getVertexCount());
        assertEquals(mesh.getTriangleCount(), loaded.getTriangleCount());
        assertTrue(loaded.hasTexCoords());
        assertTrue(loaded.hasColors());

        for (int i = 0; i < mesh.getVertexCount(); i++) {
            double[] expected = mesh.getPositions().get(i);
            double[] actual = loaded.getPositions().get(i);
            for (int d = 0; d < 3; d++) {
                assertEquals(expected[d], actual[d], "position " + i + "[" + d + "]");
            }
        }

        assertEquals(List.of(100L, 200L, 300L), loaded.getFeatureIds());
        assertEquals(List.of(3, -1, -1), loaded.getTriangleTextureIds());
        assertFalse(loaded.isTriangleColored(0));
        assertTrue(loaded.isTriangleColored(1));
        assertFalse(loaded.isTriangleColored(2));
        assertEquals(ROOF, loaded.getTriangleSurfaceType(0));
        assertEquals(WALL, loaded.getTriangleSurfaceType(1));
        assertEquals(WALL, loaded.getTriangleSurfaceType(2));
        assertEquals(TriangleMesh.OUTLINE_ALL_EDGES, loaded.getTriangleOutlineEdges(0));
        assertEquals(TriangleMesh.OUTLINE_EDGE_12, loaded.getTriangleOutlineEdges(1));
        assertEquals(0, loaded.getTriangleOutlineEdges(2));

        for (int i = 0; i < mesh.getTriangleCount(); i++) {
            assertEquals(mesh.getTriangles().get(i)[0], loaded.getTriangles().get(i)[0]);
            assertEquals(mesh.getTriangles().get(i)[1], loaded.getTriangles().get(i)[1]);
            assertEquals(mesh.getTriangles().get(i)[2], loaded.getTriangles().get(i)[2]);
        }
    }
}
