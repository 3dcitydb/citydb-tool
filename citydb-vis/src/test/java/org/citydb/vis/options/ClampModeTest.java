/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences (HFT Stuttgart) <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.options;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the {@code --clamp-to-ground} token parser. The accepted
 * spellings are user-facing CLI and JSON config values, so both the mapping and
 * the rejection message are part of the contract.
 */
class ClampModeTest {

    @ParameterizedTest
    @ValueSource(strings = {"ellipsoid", "ELLIPSOID", "  ellipsoid  "})
    void acceptsEllipsoidCaseInsensitivelyAndTrimmed(String token) {
        assertEquals(ClampMode.ELLIPSOID, ClampMode.fromValue(token));
    }

    @ParameterizedTest
    @ValueSource(strings = {"cesium-world-terrain", "Cesium-World-Terrain"})
    void acceptsCesiumWorldTerrain(String token) {
        assertEquals(ClampMode.CESIUM_WORLD_TERRAIN, ClampMode.fromValue(token));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "  ", "cesium", "cesium_world_terrain", "terrain"})
    void rejectsUnknownToken(String token) {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> ClampMode.fromValue(token));
        // The message must list the accepted spellings: it is the only place a
        // user finds them after mistyping the option.
        assertTrue(ex.getMessage().contains("ellipsoid")
                        && ex.getMessage().contains("cesium-world-terrain"),
                "the rejection must enumerate the accepted values: " + ex.getMessage());
    }

    @Test
    void serializedValueRoundTrips() {
        for (ClampMode mode : ClampMode.values()) {
            assertEquals(mode, ClampMode.fromValue(mode.toString()),
                    mode + " must parse back from its own serialized token");
        }
    }
}
