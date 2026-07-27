/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences (HFT Stuttgart) <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.geometry;

import org.citydb.model.common.Matrix4x4;
import org.citydb.model.geometry.Coordinate;
import org.citydb.model.geometry.LinearRing;
import org.citydb.model.geometry.MultiSurface;
import org.citydb.model.geometry.Point;
import org.citydb.model.geometry.Polygon;
import org.citydb.vis.util.GeoTransform;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Pins the transform contract against the CopySession-based deep copy
 * (citydb-model 40579dbf): a ring referenced twice in the prototype tree is
 * copied as ONE shared clone, so the placement pass must transform each
 * distinct ring exactly once — transforming per occurrence would move shared
 * rings twice.
 */
class ImplicitInstanceTransformerTest {
    private static final double EPS = 1e-12;

    private static LinearRing ring(double... xyz) {
        Coordinate[] points = new Coordinate[xyz.length / 3];
        for (int i = 0; i < points.length; i++) {
            points[i] = Coordinate.of(xyz[3 * i], xyz[3 * i + 1], xyz[3 * i + 2]);
        }
        return LinearRing.of(points);
    }

    @Test
    void sharedPolygonIsCopiedOnceAndTransformedOnce() {
        Polygon polygon = Polygon.of(ring(
                0, 0, 0,
                10, 0, 0,
                10, 20, 5,
                0, 0, 0));
        MultiSurface prototype = MultiSurface.of(polygon, polygon);

        ImplicitInstanceTransformer.Result result = ImplicitInstanceTransformer.transform(
                prototype, Matrix4x4.identity(), Point.of(Coordinate.of(0, 0, 0)));

        MultiSurface copy = (MultiSurface) result.geometry();
        assertEquals(2, copy.getPolygons().size());
        assertSame(copy.getPolygons().get(0), copy.getPolygons().get(1),
                "shared prototype polygon must map to one shared clone");

        List<Coordinate> points = copy.getPolygons().get(0).getExteriorRing().getPoints();
        double metersPerDegLon = GeoTransform.metersPerDegreeLon(0);
        assertEquals(10 / metersPerDegLon, points.get(1).getX(), EPS,
                "X must be converted meters→degrees exactly once");
        assertEquals(20 / GeoTransform.WGS84_METERS_PER_DEGREE_LAT, points.get(2).getY(), EPS,
                "Y must be converted meters→degrees exactly once");
        assertEquals(5, points.get(2).getZ(), EPS);
    }

    @Test
    void ringMapBridgesSharedRingToItsSingleClone() {
        Polygon polygon = Polygon.of(ring(
                0, 0, 0,
                1, 0, 0,
                1, 1, 0,
                0, 0, 0));
        MultiSurface prototype = MultiSurface.of(polygon, polygon);

        ImplicitInstanceTransformer.Result result = ImplicitInstanceTransformer.copy(prototype);

        MultiSurface copy = (MultiSurface) result.geometry();
        assertEquals(1, result.ringMap().size());
        assertSame(copy.getPolygons().get(0).getExteriorRing(),
                result.ringMap().get(polygon.getExteriorRing()),
                "prototype ring must bridge to the clone ring");
    }
}
