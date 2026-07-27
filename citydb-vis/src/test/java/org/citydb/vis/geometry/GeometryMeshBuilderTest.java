/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.geometry;

import org.citydb.model.common.Name;
import org.citydb.model.common.Namespaces;
import org.citydb.model.feature.Feature;
import org.citydb.model.geometry.Coordinate;
import org.citydb.model.geometry.LinearRing;
import org.citydb.model.geometry.Polygon;
import org.citydb.model.property.FeatureProperty;
import org.citydb.model.property.GeometryProperty;
import org.citydb.model.property.RelationType;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Characterization tests for the unit-model seam of
 * {@link GeometryMeshBuilder#build}: the T-junction pass runs for EPSG:4326
 * feature geometry but is deferred for implicit-geometry prototype templates
 * ({@code localMeters}) whose local units have no metric meaning at build
 * time. A registration-time pass with the metric tolerance misread as
 * template units deformed dense scaled-up templates (0.02 units on a
 * ×30-scaled tree = 0.6 m world tolerance).
 */
class GeometryMeshBuilderTest {

    private static final Name SURFACE_TYPE = Name.of("ImplicitGeometry", Namespaces.CORE);

    private static GeometryProperty property(double[][] ring) {
        Polygon polygon = Polygon.of(LinearRing.of(
                Arrays.stream(ring)
                        .map(p -> Coordinate.of(p[0], p[1], p[2]))
                        .toList()));
        return GeometryProperty.of(Name.of("test", Namespaces.CORE), polygon);
    }

    /**
     * Two abutting squares whose shared boundary carries a T-junction: the
     * right square's edge has a mid-edge vertex the left square's edge lacks.
     */
    private static List<GeometryProperty> tJunctionPair(double scale) {
        double s = scale;
        GeometryProperty left = property(new double[][]{
                {0, 0, 0}, {s, 0, 0}, {s, s, 0}, {0, s, 0}, {0, 0, 0}
        });
        // Split right square sharing the edge x = s, with an extra vertex at
        // its midpoint (s, s/2) that the left square's edge does not have.
        GeometryProperty right = property(new double[][]{
                {s, 0, 0}, {2 * s, 0, 0}, {2 * s, s, 0}, {s, s, 0}, {s, s / 2, 0}, {s, 0, 0}
        });
        return List.of(left, right);
    }

    @Test
    void localMetersDefersTJunctionPass() {
        // Template-local units: the tree-template scale where the misapplied
        // metric tolerance deformed geometry. The mid-edge vertex lies exactly
        // on the left square's edge, so an active pass would split it.
        TriangleMesh mesh = GeometryMeshBuilder.build(tJunctionPair(0.01), 1L,
                SURFACE_TYPE, new RingAttributes(null, null, null), "test template", true);

        // 4-gon (2 tris) + 5-gon (3 tris), unsplit: the pass is deferred to
        // PrototypeRegistry.finalizePrototypes with a scale-corrected tolerance.
        assertEquals(5, mesh.getTriangleCount());
    }

    @Test
    void degreeModeResolvesTJunctions() {
        // Same topology in degree space: the exact-on-edge vertex is within
        // any positive tolerance, so the left square's edge must be split.
        TriangleMesh mesh = GeometryMeshBuilder.build(tJunctionPair(1e-5), 1L,
                SURFACE_TYPE, new RingAttributes(null, null, null), "test feature", false);

        assertEquals(6, mesh.getTriangleCount());
    }

    // ---- owner-depth sort: deepest owner wins the first-wins polygon dedup ----

    private static final Name BUILDING = Name.of("Building", Namespaces.CORE);
    private static final Name ROOF_SURFACE = Name.of("RoofSurface", Namespaces.CORE);

    private static Polygon squarePolygon(String objectId) {
        double s = 1e-5;
        return Polygon.of(LinearRing.of(List.of(
                Coordinate.of(0, 0, 0), Coordinate.of(s, 0, 0),
                Coordinate.of(s, s, 0), Coordinate.of(0, s, 0),
                Coordinate.of(0, 0, 0)))).setObjectId(objectId);
    }

    @Test
    void deeperOwnerSurfaceTypeWinsRegardlessOfInsertionOrder() {
        for (boolean deepFirst : new boolean[]{true, false}) {
            // xlink pair: two distinct Polygon objects with the same gml:id,
            // one reachable from the Building's own solid (owner depth 1),
            // one from its boundedBy RoofSurface (owner depth 2).
            Feature building = Feature.of(BUILDING);
            GeometryProperty solidProperty = GeometryProperty.of(
                    Name.of("lod2Solid", Namespaces.CORE), squarePolygon("shared"));
            building.addGeometry(solidProperty);

            Feature roof = Feature.of(ROOF_SURFACE);
            GeometryProperty roofProperty = GeometryProperty.of(
                    Name.of("lod2MultiSurface", Namespaces.CORE), squarePolygon("shared"));
            roof.addGeometry(roofProperty);
            building.addFeature(FeatureProperty.of(
                    Name.of("boundedBy", Namespaces.CORE), roof, RelationType.CONTAINS));

            List<GeometryProperty> properties = deepFirst
                    ? List.of(roofProperty, solidProperty)
                    : List.of(solidProperty, roofProperty);
            TriangleMesh mesh = GeometryMeshBuilder.build(properties, 1L, BUILDING,
                    new RingAttributes(null, null, null));

            // One shared polygon, triangulated once (first-wins dedup) ...
            assertEquals(2, mesh.getTriangleCount(),
                    "deepFirst=" + deepFirst + ": shared polygon must be triangulated once");
            // ... and recorded with the deepest owner's feature type: the
            // depth sort must make insertion order irrelevant.
            for (int t = 0; t < mesh.getTriangleCount(); t++) {
                assertEquals(ROOF_SURFACE, mesh.getTriangleSurfaceType(t),
                        "deepFirst=" + deepFirst + ": triangle " + t
                                + " must carry the BoundarySurface type");
            }
        }
    }
}
