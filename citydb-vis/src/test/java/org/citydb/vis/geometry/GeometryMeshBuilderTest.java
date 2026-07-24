/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.geometry;

import org.citydb.model.common.Name;
import org.citydb.model.common.Namespaces;
import org.citydb.model.geometry.Coordinate;
import org.citydb.model.geometry.LinearRing;
import org.citydb.model.geometry.Polygon;
import org.citydb.model.property.GeometryProperty;
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
}
