/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences (HFT Stuttgart) <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.options;

import org.citydb.core.json.JsonMapping;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins down that the Cesium ion token round-trips through a JSON config file in
 * plaintext, matching how the database password is handled
 * ({@code org.citydb.database.connection.ConnectionDetails#password}). The CLI
 * controller then applies precedence {@code --cesium-ion-token} > config >
 * {@code CESIUM_ION_TOKEN}.
 */
class VisFormatOptionsTokenTest {

    @Test
    void tokenInConfigIsDeserialized() {
        String json = "{\"cesiumIonToken\":\"secret-token\",\"clampMode\":\"cesium-world-terrain\"}";

        I3SFormatOptions options = JsonMapping.mapper().readValue(json, I3SFormatOptions.class);

        assertEquals("secret-token", options.getCesiumIonToken(),
                "the ion token must be read from the config file (plaintext, like the DB password)");
        assertEquals(ClampMode.CESIUM_WORLD_TERRAIN, options.getClampMode());
    }

    @Test
    void tokenIsSerialized() {
        I3SFormatOptions options = new I3SFormatOptions();
        options.setCesiumIonToken("secret-token");

        String json = JsonMapping.mapper().writeValueAsString(options);

        assertTrue(json.contains("secret-token"),
                "the ion token must be written to config in plaintext (like the DB password)");
    }

    @Test
    void clampModeUsesKebabCaseInConfig() {
        // The JSON config spelling must match the CLI --clamp-to-ground spelling
        // exactly (kebab-case), not the enum-constant-name form.
        I3SFormatOptions options = new I3SFormatOptions();
        options.setClampMode(ClampMode.CESIUM_WORLD_TERRAIN);

        String json = JsonMapping.mapper().writeValueAsString(options);

        assertTrue(json.contains("\"clampMode\":\"cesium-world-terrain\""),
                "clampMode must serialize as kebab-case, matching the CLI spelling: " + json);

        I3SFormatOptions roundTripped = JsonMapping.mapper().readValue(json, I3SFormatOptions.class);
        assertEquals(ClampMode.CESIUM_WORLD_TERRAIN, roundTripped.getClampMode());
    }
}
