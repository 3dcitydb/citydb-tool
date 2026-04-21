/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences (HFT Stuttgart) <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.model.tiles3d;

import com.alibaba.fastjson2.annotation.JSONType;
import org.citydb.vis.scene.BoundingVolume;

/**
 * 3D Tiles bounding volume expressed as a geographic {@code region}:
 * {@code [west, south, east, north, minHeight, maxHeight]} in radians/meters.
 */
@JSONType(alphabetic = false)
public record TileBoundingVolume(double[] region) {

    public static TileBoundingVolume fromExtent(double[] extent) {
        return region(extent[0], extent[1], extent[2], extent[3], extent[4], extent[5]);
    }

    /**
     * Create a region bounding volume from a {@link BoundingVolume}.
     * Uses the stored AABB directly for accurate bounds.
     */
    public static TileBoundingVolume fromBoundingVolume(BoundingVolume bv) {
        if (bv == null) {
            return new TileBoundingVolume(new double[6]);
        }
        return region(bv.getMinX(), bv.getMinY(), bv.getMinZ(),
                bv.getMaxX(), bv.getMaxY(), bv.getMaxZ());
    }

    private static TileBoundingVolume region(double minX, double minY, double minZ,
                                             double maxX, double maxY, double maxZ) {
        return new TileBoundingVolume(new double[]{
                Math.toRadians(minX), Math.toRadians(minY),
                Math.toRadians(maxX), Math.toRadians(maxY),
                minZ, maxZ
        });
    }
}
