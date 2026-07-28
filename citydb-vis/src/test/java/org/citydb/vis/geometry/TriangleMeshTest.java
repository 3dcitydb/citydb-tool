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
 * plus the basic add/accessor path, asserting that the six lanes
 * (vertices / featureId / textureId / colored / surfaceType / outlineEdges)
 * stay index-aligned.
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
    void outlineEdgeMaskDefaultsToZeroAndSurvivesMergeAndDedup() {
        // The mask-less addTriangle overload must record an empty mask.
        TriangleMesh plain = new TriangleMesh();
        addTri(plain, 1L, -1, false, WALL);
        assertEquals(0, plain.getTriangleOutlineEdges(0));

        TriangleMesh a = new TriangleMesh();
        int v0 = a.addVertex(0, 0, 0, 0, 0, 1);
        int v1 = a.addVertex(1, 0, 0, 0, 0, 1);
        int v2 = a.addVertex(0, 1, 0, 0, 0, 1);
        a.addTriangle(v0, v1, v2, 100L, -1, false, ROOF, TriangleMesh.OUTLINE_ALL_EDGES);

        TriangleMesh b = new TriangleMesh();
        int w0 = b.addVertex(5, 5, 0, 0, 0, 1);
        int w1 = b.addVertex(6, 5, 0, 0, 0, 1);
        int w2 = b.addVertex(5, 6, 0, 0, 0, 1);
        b.addTriangle(w0, w1, w2, 200L, -1, false, WALL, TriangleMesh.OUTLINE_EDGE_12);

        a.merge(b);
        assertEquals(TriangleMesh.OUTLINE_ALL_EDGES, a.getTriangleOutlineEdges(0));
        assertEquals(TriangleMesh.OUTLINE_EDGE_12, a.getTriangleOutlineEdges(1));

        // Dedup rebuild keeps the survivor's mask (first-wins across ALL lanes).
        int v3 = a.addVertex(0, 0, 0, 0, 0, 1);
        int v4 = a.addVertex(1, 0, 0, 0, 0, 1);
        int v5 = a.addVertex(0, 1, 0, 0, 0, 1);
        a.addTriangle(v3, v5, v4, 300L, -1, false, GROUND, TriangleMesh.OUTLINE_EDGE_01);
        a.removeDuplicateTriangles();

        assertEquals(2, a.getTriangleCount());
        assertEquals(TriangleMesh.OUTLINE_ALL_EDGES, a.getTriangleOutlineEdges(0));
        assertEquals(TriangleMesh.OUTLINE_EDGE_12, a.getTriangleOutlineEdges(1));
    }

    @Test
    void resolveTJunctionRemapsOutlineEdgeMaskIntoChildren() {
        // Parent triangle with ALL edges outlined; the T-junction splits the
        // v0->v1 edge at (5,0). Expected inheritance: both halves of the split
        // edge stay outlined, the internal edge newVi<->v2 introduced by the
        // split does not, and the two untouched parent edges keep their bits.
        TriangleMesh mesh = new TriangleMesh();
        int a0 = mesh.addVertex(0, 0, 0, 0, 0, 1);
        int a1 = mesh.addVertex(10, 0, 0, 0, 0, 1);
        int a2 = mesh.addVertex(0, 10, 0, 0, 0, 1);
        mesh.addTriangle(a0, a1, a2, 100L, -1, false, ROOF,
                TriangleMesh.OUTLINE_ALL_EDGES);

        // Junction vertex at the midpoint of a0-a1, owned by a second triangle
        // whose own split-free mask must remain untouched.
        int b0 = mesh.addVertex(5, 0, 0, 0, 0, 1);
        int b1 = mesh.addVertex(5, -5, 0, 0, 0, 1);
        int b2 = mesh.addVertex(6, -5, 0, 0, 0, 1);
        mesh.addTriangle(b0, b1, b2, 200L, -1, false, WALL,
                TriangleMesh.OUTLINE_EDGE_20);

        mesh.resolveTJunctions(1.0, 1.0, 0.01);
        assertEquals(3, mesh.getTriangleCount());

        // Survivor B keeps its mask verbatim.
        assertEquals(200L, mesh.getFeatureIds().get(0));
        assertEquals(TriangleMesh.OUTLINE_EDGE_20, mesh.getTriangleOutlineEdges(0));

        // Identify the children by which original corner they contain:
        // child1 = (a0, newVi, a2) contains x=0 corners only;
        // child2 = (newVi, a1, a2) contains the x=10 corner.
        List<double[]> pos = mesh.getPositions();
        for (int i = 1; i <= 2; i++) {
            int[] tri = mesh.getTriangles().get(i);
            boolean containsA1 = false;
            for (int v : tri) {
                containsA1 |= pos.get(v)[0] == 10.0;
            }
            if (containsA1) {
                // child2 = (newVi, a1, a2): newVi->a1 is the outlined split
                // half, a1->a2 the untouched parent edge, a2->newVi internal.
                assertEquals(TriangleMesh.OUTLINE_EDGE_01 | TriangleMesh.OUTLINE_EDGE_12,
                        mesh.getTriangleOutlineEdges(i), "child2 mask");
            } else {
                // child1 = (a0, newVi, a2): a0->newVi outlined split half,
                // newVi->a2 internal, a2->a0 untouched parent edge.
                assertEquals(TriangleMesh.OUTLINE_EDGE_01 | TriangleMesh.OUTLINE_EDGE_20,
                        mesh.getTriangleOutlineEdges(i), "child1 mask");
            }
        }
    }

    @Test
    void resolveTJunctionSplitOfUnoutlinedEdgeStaysUnoutlined() {
        // Same split geometry, but the split edge v0->v1 itself is NOT
        // outlined — neither half may gain a bit; the other two parent
        // edges keep theirs.
        TriangleMesh mesh = new TriangleMesh();
        int a0 = mesh.addVertex(0, 0, 0, 0, 0, 1);
        int a1 = mesh.addVertex(10, 0, 0, 0, 0, 1);
        int a2 = mesh.addVertex(0, 10, 0, 0, 0, 1);
        mesh.addTriangle(a0, a1, a2, 100L, -1, false, ROOF,
                (byte) (TriangleMesh.OUTLINE_EDGE_12 | TriangleMesh.OUTLINE_EDGE_20));

        int b0 = mesh.addVertex(5, 0, 0, 0, 0, 1);
        int b1 = mesh.addVertex(5, -5, 0, 0, 0, 1);
        int b2 = mesh.addVertex(6, -5, 0, 0, 0, 1);
        mesh.addTriangle(b0, b1, b2, 200L, -1, false, WALL, (byte) 0);

        mesh.resolveTJunctions(1.0, 1.0, 0.01);
        assertEquals(3, mesh.getTriangleCount());

        List<double[]> pos = mesh.getPositions();
        for (int i = 1; i <= 2; i++) {
            int[] tri = mesh.getTriangles().get(i);
            boolean containsA1 = false;
            for (int v : tri) {
                containsA1 |= pos.get(v)[0] == 10.0;
            }
            if (containsA1) {
                // child2 = (newVi, a1, a2): only a1->a2 (parent edge 1-2) set.
                assertEquals(TriangleMesh.OUTLINE_EDGE_12,
                        mesh.getTriangleOutlineEdges(i), "child2 mask");
            } else {
                // child1 = (a0, newVi, a2): only a2->a0 (parent edge 2-0) set.
                assertEquals(TriangleMesh.OUTLINE_EDGE_20,
                        mesh.getTriangleOutlineEdges(i), "child1 mask");
            }
        }
    }

    // ---- clamp to ground ----------------------------------------------------

    /**
     * Add a triangle whose three vertices sit at the given heights, at
     * distinct XY positions so nothing welds.
     */
    private static void addTriAtHeights(TriangleMesh mesh, double z0, double z1, double z2) {
        int base = mesh.getVertexCount();
        int v0 = mesh.addVertex(base, 0, z0, 0, 0, 1);
        int v1 = mesh.addVertex(base + 1, 0, z1, 0, 0, 1);
        int v2 = mesh.addVertex(base, 1, z2, 0, 0, 1);
        mesh.addTriangle(v0, v1, v2, 1L, -1, false, WALL);
    }

    private static double minZ(TriangleMesh mesh) {
        double min = Double.MAX_VALUE;
        for (double[] pos : mesh.getPositions()) {
            min = Math.min(min, pos[2]);
        }
        return min;
    }

    @Test
    void clampToGroundTranslatesEveryVertexRigidlyOntoTheGroundHeight() {
        TriangleMesh mesh = new TriangleMesh();
        addTriAtHeights(mesh, 112.0, 118.0, 112.0);
        // The lowest vertex is added LAST and belongs to a different triangle:
        // clamping must key off the mesh minimum, not the first vertex, the
        // first triangle or the centroid.
        addTriAtHeights(mesh, 104.0, 100.0, 106.0);
        List<double[]> before = mesh.getPositions().stream().map(double[]::clone).toList();

        mesh.clampToGround(0.0);

        assertEquals(0.0, minZ(mesh), 1e-9, "the lowest vertex must land on the ground height");
        List<double[]> after = mesh.getPositions();
        for (int i = 0; i < before.size(); i++) {
            // A single shared shift: relative heights are what make the mesh a
            // building rather than a flat footprint, so every vertex moves by
            // the same delta and X/Y never move at all.
            assertEquals(before.get(i)[2] - 100.0, after.get(i)[2], 1e-9, "z of vertex " + i);
            assertEquals(before.get(i)[0], after.get(i)[0], "x of vertex " + i);
            assertEquals(before.get(i)[1], after.get(i)[1], "y of vertex " + i);
        }
    }

    @Test
    void clampToGroundLiftsOntoTerrainAndDropsBelowTheEllipsoid() {
        // --clamp-to-ground=cesium-world-terrain passes a sampled terrain
        // height, which is routinely above (mountains) or below (Dead Sea,
        // Death Valley) the ellipsoid, and the shift is signed either way.
        TriangleMesh onTerrain = new TriangleMesh();
        addTriAtHeights(onTerrain, 10.0, 22.0, 10.0);
        onTerrain.clampToGround(250.0);
        assertEquals(250.0, minZ(onTerrain), 1e-9);
        assertEquals(262.0, onTerrain.getPositions().get(1)[2], 1e-9, "12 m of height preserved");

        TriangleMesh belowEllipsoid = new TriangleMesh();
        addTriAtHeights(belowEllipsoid, 10.0, 22.0, 10.0);
        belowEllipsoid.clampToGround(-86.5);
        assertEquals(-86.5, minZ(belowEllipsoid), 1e-9);
    }

    @Test
    void clampToGroundIsANoOpWhenTheMeshAlreadySitsOnTheGround() {
        TriangleMesh mesh = new TriangleMesh();
        addTriAtHeights(mesh, 0.0, 9.0, 3.0);

        mesh.clampToGround(0.0);

        assertEquals(0.0, mesh.getPositions().get(0)[2]);
        assertEquals(9.0, mesh.getPositions().get(1)[2]);
        assertEquals(3.0, mesh.getPositions().get(2)[2]);
    }

    @Test
    void clampToGroundOnAnEmptyMeshDoesNotThrow() {
        TriangleMesh empty = new TriangleMesh();
        empty.clampToGround(120.0);
        assertTrue(empty.isEmpty());
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
