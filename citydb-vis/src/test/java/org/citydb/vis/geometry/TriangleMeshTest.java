/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.geometry;

import org.citydb.model.common.Name;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression tests pinning the per-triangle attribute alignment invariant that
 * the {@code TriangleData} struct-of-arrays refactor consolidated. Each test
 * exercises one of the three methods that used to re-implement the alignment by
 * hand ({@code merge}, {@code resolveTJunctions}, {@code removeDuplicateTriangles})
 * plus the basic add/accessor path, asserting that the five lanes
 * (vertices / featureId / textureId / colored / surfaceType) stay index-aligned.
 */
class TriangleMeshTest {

    private static final Name ROOF = Name.of("RoofSurface");
    private static final Name WALL = Name.of("WallSurface");
    private static final Name GROUND = Name.of("GroundSurface");

    /** Add a degenerate-but-valid triangle with three fresh vertices. */
    private static void addTri(TriangleMesh mesh, long featureId, int textureId,
                               boolean colored, Name surfaceType) {
        int v0 = mesh.addVertex(0, 0, 0, 0, 0, 1);
        int v1 = mesh.addVertex(1, 0, 0, 0, 0, 1);
        int v2 = mesh.addVertex(0, 1, 0, 0, 0, 1);
        mesh.addTriangle(v0, v1, v2, featureId, textureId, colored, surfaceType);
    }

    @Test
    void addTriangleKeepsAllLanesAligned() {
        TriangleMesh mesh = new TriangleMesh();
        addTri(mesh, 10L, 0, true, ROOF);
        addTri(mesh, 11L, -1, false, WALL);
        addTri(mesh, 12L, 5, true, GROUND);

        assertEquals(3, mesh.getTriangleCount());
        assertFalse(mesh.isEmpty());

        assertEquals(List.of(10L, 11L, 12L), mesh.getFeatureIds());
        assertEquals(List.of(0, -1, 5), mesh.getTriangleTextureIds());

        assertTrue(mesh.isTriangleColored(0));
        assertFalse(mesh.isTriangleColored(1));
        assertTrue(mesh.isTriangleColored(2));

        assertSame(ROOF, mesh.getTriangleSurfaceType(0));
        assertSame(WALL, mesh.getTriangleSurfaceType(1));
        assertSame(GROUND, mesh.getTriangleSurfaceType(2));
    }

    @Test
    void mergeShiftsVertexIndicesAndColoredBits() {
        TriangleMesh a = new TriangleMesh();
        addTri(a, 100L, 1, true, ROOF);        // a: 3 vertices, 1 triangle (colored)

        TriangleMesh b = new TriangleMesh();
        addTri(b, 200L, -1, false, WALL);      // b tri 0: not colored
        addTri(b, 201L, -1, true, GROUND);     // b tri 1: colored
        int bVertexCount = b.getVertexCount();

        int aVertexCount = a.getVertexCount();
        a.merge(b);

        // Vertices concatenated.
        assertEquals(aVertexCount + bVertexCount, a.getVertexCount());
        assertEquals(3, a.getTriangleCount());

        // Per-triangle attributes preserved in append order.
        assertEquals(List.of(100L, 200L, 201L), a.getFeatureIds());
        assertEquals(List.of(1, -1, -1), a.getTriangleTextureIds());
        assertSame(ROOF, a.getTriangleSurfaceType(0));
        assertSame(WALL, a.getTriangleSurfaceType(1));
        assertSame(GROUND, a.getTriangleSurfaceType(2));

        // Colored bits: a's original bit at 0, b's second triangle at 2.
        assertTrue(a.isTriangleColored(0));
        assertFalse(a.isTriangleColored(1));
        assertTrue(a.isTriangleColored(2));

        // b's triangle vertex indices shifted by a's vertex count.
        List<int[]> tris = a.getTriangles();
        for (int v : tris.get(1)) {
            assertTrue(v >= aVertexCount, "merged-in vertex index must be shifted past a's vertices");
        }
    }

    @Test
    void removeDuplicateKeepsFirstOccurrenceWithItsAttributes() {
        TriangleMesh mesh = new TriangleMesh();
        // Three coincident vertices reused by two triangles with opposite winding.
        int v0 = mesh.addVertex(0, 0, 0, 0, 0, 1);
        int v1 = mesh.addVertex(1, 0, 0, 0, 0, 1);
        int v2 = mesh.addVertex(0, 1, 0, 0, 0, 1);

        // T0: the surviving original (colored, ROOF).
        mesh.addTriangle(v0, v1, v2, 100L, 7, true, ROOF);
        // T1: same positions, reversed winding -> duplicate, must be dropped.
        mesh.addTriangle(v2, v1, v0, 200L, -1, false, WALL);
        // T2: a distinct triangle, must be kept.
        int v3 = mesh.addVertex(5, 5, 0, 0, 0, 1);
        mesh.addTriangle(v0, v1, v3, 300L, 9, false, GROUND);

        mesh.removeDuplicateTriangles();

        assertEquals(2, mesh.getTriangleCount());
        // First-wins: T0 kept (not the reversed T1), its attributes intact.
        assertEquals(List.of(100L, 300L), mesh.getFeatureIds());
        assertEquals(List.of(7, 9), mesh.getTriangleTextureIds());
        assertTrue(mesh.isTriangleColored(0));
        assertFalse(mesh.isTriangleColored(1));
        assertSame(ROOF, mesh.getTriangleSurfaceType(0));
        assertSame(GROUND, mesh.getTriangleSurfaceType(1));
    }

    @Test
    void resolveTJunctionSplitsEdgeAndInheritsParentAttributes() {
        TriangleMesh mesh = new TriangleMesh();
        // Triangle A: long edge v0->v1 along x, colored ROOF.
        int a0 = mesh.addVertex(0, 0, 0, 0, 0, 1);
        int a1 = mesh.addVertex(10, 0, 0, 0, 0, 1);
        int a2 = mesh.addVertex(0, 10, 0, 0, 0, 1);
        mesh.addTriangle(a0, a1, a2, 100L, -1, true, ROOF);

        // Triangle B: has a vertex exactly on the midpoint of A's edge v0-v1,
        // which is a textbook T-junction. B itself is uncolored WALL.
        int b0 = mesh.addVertex(5, 0, 0, 0, 0, 1);    // on edge a0-a1
        int b1 = mesh.addVertex(5, -5, 0, 0, 0, 1);
        int b2 = mesh.addVertex(6, -5, 0, 0, 0, 1);
        mesh.addTriangle(b0, b1, b2, 200L, -1, false, WALL);

        int vertsBefore = mesh.getVertexCount();
        // scaleX=scaleY=1 -> positions are effectively metres; tolerance 1 cm.
        mesh.resolveTJunctions(1.0, 1.0, 0.01);

        // A split into two children; B survives. Total 3 triangles.
        assertEquals(3, mesh.getTriangleCount());
        // One new vertex (the split point) was added.
        assertEquals(vertsBefore + 1, mesh.getVertexCount());

        // Rebuild order: survivors first (B), then the split children (A x2).
        assertEquals(200L, mesh.getFeatureIds().get(0));
        assertFalse(mesh.isTriangleColored(0));
        assertSame(WALL, mesh.getTriangleSurfaceType(0));

        // Both split children inherit A's featureId / colored flag / surface type.
        for (int i = 1; i <= 2; i++) {
            assertEquals(100L, mesh.getFeatureIds().get(i), "child " + i + " featureId");
            assertTrue(mesh.isTriangleColored(i), "child " + i + " colored bit");
            assertSame(ROOF, mesh.getTriangleSurfaceType(i), "child " + i + " surface type");
        }

        // Lanes remain the same length (alignment invariant).
        assertEquals(mesh.getTriangleCount(), mesh.getFeatureIds().size());
        assertEquals(mesh.getTriangleCount(), mesh.getTriangleTextureIds().size());
    }

    @Test
    void emptyAndTrivialMeshesAreNoOps() {
        TriangleMesh empty = new TriangleMesh();
        assertTrue(empty.isEmpty());
        assertEquals(0, empty.getTriangleCount());
        // Must not throw on empty input.
        empty.resolveTJunctions(1.0, 1.0, 0.01);
        empty.removeDuplicateTriangles();
        assertTrue(empty.isEmpty());

        TriangleMesh single = new TriangleMesh();
        addTri(single, 1L, 0, true, ROOF);
        single.removeDuplicateTriangles();   // <=1 triangle -> no-op
        assertEquals(1, single.getTriangleCount());
        assertTrue(single.isTriangleColored(0));
        assertSame(ROOF, single.getTriangleSurfaceType(0));
    }
}
