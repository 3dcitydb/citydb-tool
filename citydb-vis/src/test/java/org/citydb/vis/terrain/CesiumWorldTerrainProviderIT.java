/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.terrain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Live end-to-end check of {@link CesiumWorldTerrainProvider} against Cesium
 * World Terrain. Hits the network (ion handshake + tile fetch + metadata
 * descent), so it only runs when a {@code CESIUM_ION_TOKEN} environment
 * variable is present; otherwise JUnit skips it. The token is never stored in
 * source.
 * <p>
 * Cesium World Terrain heights are metres above the WGS84 <em>ellipsoid</em>,
 * so the expected values fold in the geoid undulation; assertions stay loose
 * and the sampled values are printed for visual confirmation.
 */
@EnabledIfEnvironmentVariable(named = "CESIUM_ION_TOKEN", matches = ".+")
class CesiumWorldTerrainProviderIT {

    @Test
    void samplesKnownTerrainHeights() throws Exception {
        String token = System.getenv("CESIUM_ION_TOKEN");
        try (CesiumWorldTerrainProvider provider = new CesiumWorldTerrainProvider(token)) {
            double everest = print("Mount Everest", 86.9250, 27.9881, provider);
            double stuttgart = print("Stuttgart (HFT)", 9.1746, 48.7823, provider);
            double deathValley = print("Death Valley (Badwater)", -116.7686, 36.2335, provider);
            double amsterdam = print("Amsterdam", 4.8952, 52.3702, provider);
            double everestAgain = print("Mount Everest (cache hit)", 86.9250, 27.9881, provider);

            // Loose sanity bounds — terrain, not exact survey heights.
            assertTrue(everest > 4000.0, "Everest should be high mountain terrain");
            assertTrue(stuttgart > 100.0 && stuttgart < 600.0, "Stuttgart ~250-300 m");
            assertTrue(deathValley < 200.0, "Death Valley is low/below sea level");
            assertTrue(Math.abs(amsterdam) < 100.0, "Amsterdam is near sea level");
            assertFalse(Double.isNaN(everest), "Everest must resolve to a height");
            assertTrue(Double.compare(everest, everestAgain) == 0, "cache must be deterministic");
        }
    }

    private static double print(String label, double lon, double lat,
                                CesiumWorldTerrainProvider provider) {
        double h = provider.sampleHeight(lon, lat);
        System.out.printf("  %-28s lon=%9.4f lat=%8.4f -> %s%n",
                label, lon, lat, Double.isNaN(h) ? "NaN" : String.format("%.1f m", h));
        return h;
    }
}
