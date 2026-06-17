/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences (HFT Stuttgart) <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.terrain;

/**
 * Samples terrain elevation at geographic points, used by the writer to bake a
 * ground height into each feature for {@code --clamp-to-ground=cesium-world-terrain}.
 * <p>
 * Implementations must be safe for concurrent {@link #sampleHeight} calls — the
 * writer samples once per feature from its async processing pool.
 */
public interface TerrainElevationProvider extends AutoCloseable {

    /**
     * Terrain height in metres above the WGS84 ellipsoid at the given point
     * (longitude/latitude in degrees, WGS84). Returns {@link Double#NaN} when
     * no height could be sampled (point outside coverage, tile unavailable, or
     * a recoverable fetch/decode error); callers decide the fallback.
     */
    double sampleHeight(double lon, double lat);

    @Override
    void close();
}
