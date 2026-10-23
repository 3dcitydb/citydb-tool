/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences <https://www.hft-stuttgart.de>
 */
package org.citydb.vis.encoder.tiles3d;

import org.citydb.model.common.Name;
import org.citydb.vis.encoder.TriangleRouter;
import org.citydb.vis.encoder.TriangleRouter.RoutedTriangle;
import org.citydb.vis.geometry.TriangleMesh;
import org.citydb.vis.geometry.VertexWelder;
import org.citydb.vis.styling.ObjectStyleRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Pins the primitive builder's defensive behaviour on inconsistent routing
 * input: a triangle may be flagged colored while its mesh carries no color
 * lane, and the colored page must still build rather than fail on lookup.
 */
class GlbPrimitiveBuilderTest {
    private static final Name TYPE = Name.of("Building");

    /**
     * A triangle flagged colored on a mesh that never grew a colour lane
     * must still build as a colored primitive (white, opaque) instead of
     * failing on the vertex-colour lookup.
     */
    @Test
    void coloredPageWithoutColorLaneFallsBackToWhite() {
        TriangleMesh mesh = new TriangleMesh();
        int v0 = mesh.addVertex(0, 0, 0, 0, 0, 1);
        int v1 = mesh.addVertex(1, 0, 0, 0, 0, 1);
        int v2 = mesh.addVertex(0, 1, 0, 0, 0, 1);
        mesh.addTriangle(v0, v1, v2, 1L, -1, true, TYPE, (byte) 0);

        VertexWelder.WeldResult weld = VertexWelder.weldAndFilter(mesh, 0.5, 0.5, 0);
        List<RoutedTriangle> routed = TriangleRouter.route(mesh, weld, ObjectStyleRegistry.empty());
        TriangleRouter.PageBuckets buckets = TriangleRouter.bucketByPage(routed, 0, null);
        assertEquals(1, buckets.colored().size());

        GlbPrimitiveBuilder.PrimitiveArrays arrays = GlbPrimitiveBuilder.build(mesh, weld,
                buckets.colored(), GlbPrimitiveBuilder.UNTEXTURED_COLORED_PAGE, null,
                CellFrame.identity(), false, true, false);

        assertNotNull(arrays.colors());
        assertEquals(3 * 4, arrays.colors().length);
        for (float component : arrays.colors()) {
            assertEquals(1f, component);
        }
    }
}
