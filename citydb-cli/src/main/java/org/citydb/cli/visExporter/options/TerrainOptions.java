/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences <https://www.hft-stuttgart.de>
 */

package org.citydb.cli.visExporter.options;

import org.citydb.cli.common.Option;
import org.citydb.vis.config.ClampMode;
import picocli.CommandLine;

import java.util.Arrays;
import java.util.Iterator;
import java.util.stream.Collectors;

public class TerrainOptions implements Option {
    @CommandLine.Option(names = "--clamp-to-ground", paramLabel = "<mode>",
            converter = ClampModeConverter.class,
            completionCandidates = ClampModeCandidates.class,
            description = "Vertically clamp each feature before tiling: " +
                    "${COMPLETION-CANDIDATES}. 'ellipsoid' shifts each feature so " +
                    "its lowest point sits on the WGS84 ellipsoid (height 0) — " +
                    "useful when no terrain is loaded in the viewer. " +
                    "'cesium-world-terrain' samples the Cesium World Terrain height " +
                    "at each feature's centroid at export time (requires " +
                    "--cesium-ion-token) and bakes that as the ground height, so the " +
                    "export lines up with Cesium World Terrain in the viewer even " +
                    "when the source heights are unreliable or relative. When omitted, " +
                    "features keep their absolute database height (no clamping).")
    private ClampMode clampMode;

    @CommandLine.Option(names = "--cesium-ion-token", paramLabel = "<token>",
            description = "Cesium ion access token used to fetch Cesium World Terrain for " +
                    "--clamp-to-ground=cesium-world-terrain. May also be set in the config " +
                    "file or via the CESIUM_ION_TOKEN environment variable; precedence is " +
                    "command line > config file > environment variable. Like the database " +
                    "password, a token set in the config file is stored there in plain text.")
    private String cesiumIonToken;

    public ClampMode getClampMode() {
        return clampMode;
    }

    public String getCesiumIonToken() {
        return cesiumIonToken;
    }

    // The presence check for the Cesium ion token lives in the controller, not
    // in a preprocess() override here: the token may come from the config file
    // (CLI flag > config > CESIUM_ION_TOKEN), and preprocess() runs at parse
    // time before the config is merged, so it cannot see a config-supplied token.

    /**
     * Maps the kebab-case CLI spelling ({@code ellipsoid},
     * {@code cesium-world-terrain}) onto {@link ClampMode}; picocli's default
     * enum matching only accepts the constant names, which carry an underscore.
     */
    static class ClampModeConverter implements CommandLine.ITypeConverter<ClampMode> {
        @Override
        public ClampMode convert(String value) {
            return ClampMode.fromValue(value);
        }
    }

    /**
     * Supplies the kebab-case values for {@code ${COMPLETION-CANDIDATES}} and
     * shell completion, instead of the enum constant names.
     */
    static class ClampModeCandidates implements Iterable<String> {
        @Override
        public Iterator<String> iterator() {
            return Arrays.stream(ClampMode.values())
                    .map(ClampMode::getValue)
                    .collect(Collectors.toList())
                    .iterator();
        }
    }
}
