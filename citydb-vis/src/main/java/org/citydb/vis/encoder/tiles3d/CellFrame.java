/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.encoder.tiles3d;

import org.citydb.vis.scene.BoundingVolume;
import org.citydb.vis.util.GeoTransform;

/**
 * Scale/offset from node-local coordinates to cell-centered ENU meters.
 * Welded positions are relative to the node center as EPSG:4326 degree
 * offsets (X/Y) plus meters (Z); {@link #from} converts the degree axes
 * with per-cell meters-per-degree factors, while the {@link #identity}/
 * {@link #scaled} prototype variants take positions already in meters.
 * The offset shifts the result
 * into the cell-centered frame that the cell root's per-cell ENU-to-ECEF
 * tile transform maps into global ECEF. Because the anchor is the cell
 * center (not the whole dataset), positions stay small (bounded by the
 * cell extent) and the tangent plane is re-established at every cell, so
 * Earth curvature no longer lifts far-from-center cells off the ground.
 */
record CellFrame(double scaleX, double scaleY, double scaleZ,
                 float offsetX, float offsetY, float offsetZ) {
    /**
     * Identity frame for instanced prototype meshes: their welded
     * positions are already local Cartesian meters centered on the
     * template origin, so no degree scaling and no cell offset apply —
     * placement happens per instance via the TRS attributes.
     */
    static CellFrame identity() {
        return scaled(1.0);
    }

    /**
     * Uniform-scale frame for instanced prototype meshes, used to bake the
     * prototype's normalization scale into the encoded vertex positions:
     * CesiumJS computes an instanced model's culling bounds from the
     * prototype's POSITION extents expanded by the instance TRANSLATION
     * range only — instance SCALE is ignored, so a scaled-up template
     * yields a bounding sphere too small by that factor and the model is
     * frustum-culled (or near-plane-clipped) when the camera comes close.
     * Encoding the prototype at world size (and dividing each instance's
     * SCALE accordingly) is placement-equivalent and keeps those bounds
     * correct.
     */
    static CellFrame scaled(double scale) {
        return new CellFrame(scale, scale, scale, 0f, 0f, 0f);
    }

    static CellFrame from(BoundingVolume mbs, double[] cellCenter) {
        double scaleX = GeoTransform.metersPerDegreeLon(cellCenter[1]);
        double scaleY = GeoTransform.WGS84_METERS_PER_DEGREE_LAT;
        float offsetX = (float) ((mbs.getCenterX() - cellCenter[0]) * scaleX);
        float offsetY = (float) ((mbs.getCenterY() - cellCenter[1]) * scaleY);
        float offsetZ = (float) (mbs.getCenterZ() - cellCenter[2]);
        return new CellFrame(scaleX, scaleY, 1.0, offsetX, offsetY, offsetZ);
    }
}
