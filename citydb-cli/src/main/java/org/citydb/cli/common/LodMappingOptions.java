/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.cli.common;

import org.citydb.io.citygml.reader.options.LodMappingStrategy;
import picocli.CommandLine;

import java.util.Map;

public class LodMappingOptions implements Option {
    enum Strategy {maximum, minimum}

    @CommandLine.Option(names = "--lod-mapping-strategy", paramLabel = "<strategy>",
            description = "Strategy for mapping multiple CityJSON LoDs onto a CityGML LoD: " +
                    "${COMPLETION-CANDIDATES} (default: maximum).")
    private Strategy strategy;

    @CommandLine.Option(names = "--lod-mapping", split = ",", paramLabel = "<lod=cityjson-lod>",
            description = "Map a CityGML LoD onto a specific CityJSON LoD. " +
                    "Overrides the mapping strategy for this LoD.")
    private Map<Integer, String> mappings;

    public org.citydb.io.citygml.reader.options.LodMappingOptions toLodMappingOptions(
            org.citydb.io.citygml.reader.options.LodMappingOptions options) {
        if (options == null) {
            options = new org.citydb.io.citygml.reader.options.LodMappingOptions();
        }

        if (strategy != null) {
            options.setStrategy(switch (strategy) {
                case maximum -> LodMappingStrategy.MAXIMUM;
                case minimum -> LodMappingStrategy.MINIMUM;
            });
        }

        if (mappings != null) {
            options.setMappings(mappings);
        }

        return options;
    }
}
