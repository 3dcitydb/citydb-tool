/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences (HFT Stuttgart) <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.options;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * How the exporter places each feature vertically before tiling.
 * <p>
 * The default (no {@code --clamp-to-ground}) is <em>no clamping</em>: features
 * keep the absolute height that comes out of the database. This enum only
 * describes the two <em>active</em> clamp targets; the "do nothing" state is
 * modelled by a {@code null} {@code ClampMode} on the format options, not by a
 * constant here.
 * <ul>
 *   <li>{@link #ELLIPSOID} — shift each mesh so its lowest vertex sits on the
 *       WGS84 ellipsoid (height 0). Useful when the viewer has no terrain
 *       loaded. Purely local, no network access.</li>
 *   <li>{@link #CESIUM_WORLD_TERRAIN} — sample the Cesium World Terrain height
 *       at the feature's centroid (via a Cesium ion token) at export time and
 *       shift each mesh so its lowest vertex sits on that terrain height. The
 *       baked offsets make the export line up with Cesium World Terrain in the
 *       viewer even when the source heights are unreliable or relative.</li>
 * </ul>
 */
public enum ClampMode {
    ELLIPSOID("ellipsoid"),
    CESIUM_WORLD_TERRAIN("cesium-world-terrain");

    private final String value;

    ClampMode(String value) {
        this.value = value;
    }

    /**
     * The CLI / config spelling of this mode (kebab-case), e.g.
     * {@code cesium-world-terrain}. Also the JSON config representation, so
     * the config spelling matches the CLI {@code --clamp-to-ground} spelling
     * exactly instead of the enum constant name.
     */
    @JsonValue
    public String getValue() {
        return value;
    }

    /**
     * Resolve a CLI / config token (case-insensitive, kebab-case) to a mode.
     * Also used to read the mode from a JSON config.
     *
     * @throws IllegalArgumentException if the token matches no mode
     */
    @JsonCreator
    public static ClampMode fromValue(String value) {
        if (value != null) {
            for (ClampMode mode : values()) {
                if (mode.value.equalsIgnoreCase(value.trim())) {
                    return mode;
                }
            }
        }
        throw new IllegalArgumentException("unknown clamp mode '" + value +
                "' (expected one of: ellipsoid, cesium-world-terrain)");
    }

    @Override
    public String toString() {
        return value;
    }
}
