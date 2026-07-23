/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences (HFT Stuttgart) <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.geometry;

import org.citydb.model.common.Name;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pins the suppression rule of {@link OutlineEdgeMerger}: an edge loses its
 * outline bits only when shared by exactly two coplanar triangles of the same
 * surface type — the "one plane subdivided into many polygons" case. Folds
 * (creases), type boundaries (door against wall) and non-manifold edges keep
 * their outlines. Triangles are added with disjoint vertex ranges sharing
 * coordinates bit-for-bit, mimicking how abutting source polygons come out of
 * {@link PolygonTriangulator}.
 */
class OutlineEdgeMergerTest {
    private static final Name WALL = Name.of("WallSurface");
    private static final Name DOOR = Name.of("Door");

    /**
     * Append a triangle with its own (unshared) vertices and a full outline
     * mask, returning its index. Normals are irrelevant to the merger, which
     * derives planes from positions.
     */
    private static int addTri(TriangleMesh mesh, Name type,
                              double[] a, double[] b, double[] c) {
        int v0 = mesh.addVertex(a[0], a[1], a[2], 0, 0, 1);
        int v1 = mesh.addVertex(b[0], b[1], b[2], 0, 0, 1);
        int v2 = mesh.addVertex(c[0], c[1], c[2], 0, 0, 1);
        mesh.addTriangle(v0, v1, v2, 1L, -1, false, type, TriangleMesh.OUTLINE_ALL_EDGES);
        return mesh.getTriangleCount() - 1;
    }

    @Test
    void coplanarSameTypeSharedEdgeLosesOutline() {
        TriangleMesh mesh = new TriangleMesh();
        // Unit quad in the XY plane split along the (0,0)-(1,1) diagonal.
        int t0 = addTri(mesh, WALL, new double[]{0, 0, 0}, new double[]{1, 0, 0},
                new double[]{1, 1, 0});
        int t1 = addTri(mesh, WALL, new double[]{0, 0, 0}, new double[]{1, 1, 0},
                new double[]{0, 1, 0});

        mesh.mergeCoplanarOutlineEdges(1.0, 1.0, 0.0);

        // t0's shared edge is v2->v0 ((1,1)->(0,0)), t1's is v0->v1
        // ((0,0)->(1,1)); the two silhouette edges of each triangle survive.
        assertEquals(TriangleMesh.OUTLINE_EDGE_01 | TriangleMesh.OUTLINE_EDGE_12,
                mesh.getTriangleOutlineEdges(t0), "t0 keeps only its silhouette edges");
        assertEquals(TriangleMesh.OUTLINE_EDGE_12 | TriangleMesh.OUTLINE_EDGE_20,
                mesh.getTriangleOutlineEdges(t1), "t1 keeps only its silhouette edges");
    }

    @Test
    void creaseEdgeKeepsOutline() {
        TriangleMesh mesh = new TriangleMesh();
        // Same shared edge, but the second triangle folds 90 degrees out of
        // the XY plane.
        int t0 = addTri(mesh, WALL, new double[]{0, 0, 0}, new double[]{1, 0, 0},
                new double[]{1, 1, 0});
        int t1 = addTri(mesh, WALL, new double[]{0, 0, 0}, new double[]{1, 1, 0},
                new double[]{0, 0, 1});

        mesh.mergeCoplanarOutlineEdges(1.0, 1.0, 0.0);

        assertEquals(TriangleMesh.OUTLINE_ALL_EDGES, mesh.getTriangleOutlineEdges(t0));
        assertEquals(TriangleMesh.OUTLINE_ALL_EDGES, mesh.getTriangleOutlineEdges(t1));
    }

    @Test
    void differentSurfaceTypeKeepsOutline() {
        TriangleMesh mesh = new TriangleMesh();
        // Coplanar neighbours, but a door abutting its wall: the semantic
        // boundary must survive.
        int t0 = addTri(mesh, WALL, new double[]{0, 0, 0}, new double[]{1, 0, 0},
                new double[]{1, 1, 0});
        int t1 = addTri(mesh, DOOR, new double[]{0, 0, 0}, new double[]{1, 1, 0},
                new double[]{0, 1, 0});

        mesh.mergeCoplanarOutlineEdges(1.0, 1.0, 0.0);

        assertEquals(TriangleMesh.OUTLINE_ALL_EDGES, mesh.getTriangleOutlineEdges(t0));
        assertEquals(TriangleMesh.OUTLINE_ALL_EDGES, mesh.getTriangleOutlineEdges(t1));
    }

    @Test
    void nonManifoldEdgeKeepsOutline() {
        TriangleMesh mesh = new TriangleMesh();
        // Three coplanar triangles fanning around the same edge: multiplicity
        // is not 2, so nothing may be merged.
        int t0 = addTri(mesh, WALL, new double[]{0, 0, 0}, new double[]{1, 1, 0},
                new double[]{1, 0, 0});
        int t1 = addTri(mesh, WALL, new double[]{0, 0, 0}, new double[]{1, 1, 0},
                new double[]{0, 1, 0});
        int t2 = addTri(mesh, WALL, new double[]{0, 0, 0}, new double[]{1, 1, 0},
                new double[]{2, 2, 0});

        mesh.mergeCoplanarOutlineEdges(1.0, 1.0, 0.0);

        assertEquals(TriangleMesh.OUTLINE_ALL_EDGES, mesh.getTriangleOutlineEdges(t0));
        assertEquals(TriangleMesh.OUTLINE_ALL_EDGES, mesh.getTriangleOutlineEdges(t1));
        assertEquals(TriangleMesh.OUTLINE_ALL_EDGES, mesh.getTriangleOutlineEdges(t2));
    }

    @Test
    void flippedWindingNeighbourStillMerges() {
        TriangleMesh mesh = new TriangleMesh();
        // Second triangle wound the other way (normal points -Z): the
        // pipeline renders double-sided, so it still subdivides the same
        // plane and the shared edge is dropped.
        int t0 = addTri(mesh, WALL, new double[]{0, 0, 0}, new double[]{1, 0, 0},
                new double[]{1, 1, 0});
        int t1 = addTri(mesh, WALL, new double[]{0, 0, 0}, new double[]{0, 1, 0},
                new double[]{1, 1, 0});

        mesh.mergeCoplanarOutlineEdges(1.0, 1.0, 0.0);

        // Shared diagonal is t0's edge 20 ((1,1)->(0,0)) and t1's edge 20
        // ((1,1)->(0,0) reversed); both silhouette pairs survive.
        assertEquals(TriangleMesh.OUTLINE_EDGE_01 | TriangleMesh.OUTLINE_EDGE_12,
                mesh.getTriangleOutlineEdges(t0));
        assertEquals(TriangleMesh.OUTLINE_EDGE_01 | TriangleMesh.OUTLINE_EDGE_12,
                mesh.getTriangleOutlineEdges(t1));
    }

    /**
     * A gable roof in EPSG:4326 coordinates (X/Y in degrees, Z in meters):
     * two opposite slopes sharing the horizontal ridge. In raw degree space
     * the ~1e5 horizontal compression turns both slopes into near-vertical
     * sheets with near-parallel normals — the ridge would merge away as
     * "coplanar" (regression for the Stuttgart building DEBW_52210006KDB,
     * which lost its 47 m ridge line). With the degree-to-meter scales
     * applied, the real dihedral angle is seen and every edge survives.
     */
    @Test
    void gableRidgeInDegreeSpaceKeepsOutline() {
        double scale = 100_000;
        TriangleMesh mesh = new TriangleMesh();
        Name roof = Name.of("RoofSurface");
        // Ridge from (0,0) to (1e-4,0) at z=10 (10 m long in meters);
        // eaves 5 m north/south (5e-5 deg) at z=5 — 45-degree slopes.
        double[] r0 = {0, 0, 10}, r1 = {1e-4, 0, 10};
        int t0 = addTri(mesh, roof, r0, r1, new double[]{0, 5e-5, 5});
        int t1 = addTri(mesh, roof, r0, new double[]{0, -5e-5, 5}, r1);

        mesh.mergeCoplanarOutlineEdges(scale, scale, 0.0);

        assertEquals(TriangleMesh.OUTLINE_ALL_EDGES, mesh.getTriangleOutlineEdges(t0),
                "ridge and eaves of the north slope must keep their outline");
        assertEquals(TriangleMesh.OUTLINE_ALL_EDGES, mesh.getTriangleOutlineEdges(t1),
                "ridge and eaves of the south slope must keep their outline");
    }

    /**
     * Surveyed-facet noise: two same-type neighbours meeting at a small
     * (~2.8 degree) dihedral. Strict mode (max angle 0) keeps the seam; a
     * user-supplied max angle of 5 degrees merges it — the escape hatch for
     * surveyed LoD2 data whose facet angles are continuous rather than
     * exactly coplanar.
     */
    @Test
    void surveyedFacetSeamMergesOnlyWithinMaxAngle() {
        for (double maxAngle : new double[]{0.0, 5.0}) {
            TriangleMesh mesh = new TriangleMesh();
            // Lifting one vertex by tan(2 deg) tilts the second plane by
            // sqrt(2)*2 deg, roughly 2.8 degrees of true dihedral.
            double tilt = Math.tan(Math.toRadians(2));
            int t0 = addTri(mesh, WALL, new double[]{0, 0, 0}, new double[]{1, 0, 0},
                    new double[]{1, 1, 0});
            int t1 = addTri(mesh, WALL, new double[]{0, 0, 0}, new double[]{1, 1, 0},
                    new double[]{0, 1, tilt});

            mesh.mergeCoplanarOutlineEdges(1.0, 1.0, maxAngle);

            if (maxAngle == 0.0) {
                assertEquals(TriangleMesh.OUTLINE_ALL_EDGES, mesh.getTriangleOutlineEdges(t0),
                        "2-degree seam must survive strict mode");
                assertEquals(TriangleMesh.OUTLINE_ALL_EDGES, mesh.getTriangleOutlineEdges(t1));
            } else {
                assertEquals(TriangleMesh.OUTLINE_EDGE_01 | TriangleMesh.OUTLINE_EDGE_12,
                        mesh.getTriangleOutlineEdges(t0),
                        "2-degree seam must merge within a 5-degree max angle");
                assertEquals(TriangleMesh.OUTLINE_EDGE_12 | TriangleMesh.OUTLINE_EDGE_20,
                        mesh.getTriangleOutlineEdges(t1));
            }
        }
    }

    /**
     * A 400x250 TIN plane — 200k triangles, twice the T-junction pass's size
     * ceiling: the merge deliberately has no such ceiling (counting-sort
     * bucket grouping keeps it linear), because huge TIN features are where
     * wireframe noise is worst. Only the rectangle's silhouette may survive.
     */
    @Test
    void largeTinMergesWithoutSizeCeiling() {
        int nx = 400, ny = 250;
        TriangleMesh mesh = new TriangleMesh();
        for (int qx = 0; qx < nx; qx++) {
            for (int qy = 0; qy < ny; qy++) {
                double[] p00 = {qx, qy, 0}, p10 = {qx + 1, qy, 0};
                double[] p11 = {qx + 1, qy + 1, 0}, p01 = {qx, qy + 1, 0};
                addTri(mesh, WALL, p00, p10, p11);
                addTri(mesh, WALL, p00, p11, p01);
            }
        }
        assertEquals(2 * nx * ny, mesh.getTriangleCount());

        mesh.mergeCoplanarOutlineEdges(1.0, 1.0, 0.0);

        int outlinedEdges = 0;
        for (int t = 0; t < mesh.getTriangleCount(); t++) {
            outlinedEdges += Integer.bitCount(mesh.getTriangleOutlineEdges(t));
        }
        assertEquals(2 * (nx + ny), outlinedEdges,
                "only the outer silhouette may stay outlined");
    }

    /**
     * A 2x2 TIN wall: eight coplanar triangles tiling a square. Only the
     * square's silhouette may keep outlines — every interior edge (quad
     * diagonals and quad-to-quad seams) is dropped.
     */
    @Test
    void tinWallKeepsOnlySilhouette() {
        TriangleMesh mesh = new TriangleMesh();
        for (int qx = 0; qx < 2; qx++) {
            for (int qy = 0; qy < 2; qy++) {
                double[] p00 = {qx, qy, 0}, p10 = {qx + 1, qy, 0};
                double[] p11 = {qx + 1, qy + 1, 0}, p01 = {qx, qy + 1, 0};
                addTri(mesh, WALL, p00, p10, p11);
                addTri(mesh, WALL, p00, p11, p01);
            }
        }

        mesh.mergeCoplanarOutlineEdges(1.0, 1.0, 0.0);

        int outlinedEdges = 0;
        for (int t = 0; t < mesh.getTriangleCount(); t++) {
            outlinedEdges += Integer.bitCount(mesh.getTriangleOutlineEdges(t));
        }
        // Silhouette of the 2x2 square = 8 unit edges, each used by exactly
        // one triangle.
        assertEquals(8, outlinedEdges, "only the outer silhouette may stay outlined");
    }
}
