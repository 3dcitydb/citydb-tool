/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.vis.model.tiles3d;

import com.alibaba.fastjson2.annotation.JSONType;
import org.citydb.vis.scene.BoundingVolume;

@JSONType(alphabetic = false)
public record TileBoundingVolume(double[] region) {
    private static final double METERS_PER_DEGREE_LAT = 111_320.0;

    public static TileBoundingVolume fromExtent(double[] extent) {
        return new TileBoundingVolume(new double[]{
                Math.toRadians(extent[0]), Math.toRadians(extent[1]),
                Math.toRadians(extent[3]), Math.toRadians(extent[4]),
                extent[2], extent[5]
        });
    }

    public static TileBoundingVolume fromMbs(BoundingVolume bv) {
        if (bv == null) {
            return new TileBoundingVolume(new double[6]);
        }
        double centerLon = bv.getCenterX();
        double centerLat = bv.getCenterY();
        double centerAlt = bv.getCenterZ();
        double radius = bv.getRadius();

        double radiusDegreesLat = radius / METERS_PER_DEGREE_LAT;
        double cosLat = Math.cos(Math.toRadians(centerLat));
        double radiusDegreesLon = cosLat > 0
                ? radius / (METERS_PER_DEGREE_LAT * cosLat) : radiusDegreesLat;

        return new TileBoundingVolume(new double[]{
                Math.toRadians(centerLon - radiusDegreesLon),
                Math.toRadians(centerLat - radiusDegreesLat),
                Math.toRadians(centerLon + radiusDegreesLon),
                Math.toRadians(centerLat + radiusDegreesLat),
                centerAlt - radius,
                centerAlt + radius
        });
    }
}
