/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
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
        return new TileBoundingVolume(new double[]{
                Math.toRadians(extent[0]), Math.toRadians(extent[1]),
                Math.toRadians(extent[3]), Math.toRadians(extent[4]),
                extent[2], extent[5]
        });
    }

    /**
     * Create a region bounding volume from a {@link BoundingVolume}.
     * Uses the stored AABB directly for accurate bounds.
     */
    public static TileBoundingVolume fromBoundingVolume(BoundingVolume bv) {
        if (bv == null) {
            return new TileBoundingVolume(new double[6]);
        }
        return new TileBoundingVolume(new double[]{
                Math.toRadians(bv.getMinX()), Math.toRadians(bv.getMinY()),
                Math.toRadians(bv.getMaxX()), Math.toRadians(bv.getMaxY()),
                bv.getMinZ(), bv.getMaxZ()
        });
    }
}
