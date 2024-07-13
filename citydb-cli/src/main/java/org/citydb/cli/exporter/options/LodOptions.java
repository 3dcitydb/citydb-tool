/*
 * citydb-tool - Command-line tool for the 3D City Database
 * https://www.3dcitydb.org/
 *
 * Copyright 2022-2024
 * virtualcitysystems GmbH, Germany
 * https://vc.systems/
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.citydb.cli.exporter.options;

import org.citydb.cli.common.Option;
import org.citydb.operation.exporter.options.LodMode;
import org.citydb.query.lod.LodFilter;
import org.citydb.query.lod.LodFilterMode;
import picocli.CommandLine;

import java.util.Arrays;
import java.util.LinkedHashSet;

public class LodOptions implements Option {
    private enum Mode {or, and, minimum, maximum}

    @CommandLine.Option(names = {"-l", "--lod"}, split = ",", paramLabel = "<lod>",
            description = "Export features with a matching LoD.")
    private String[] lods;

    @CommandLine.Option(names = "--lod-mode", defaultValue = "or",
            description = "LoD filter mode: ${COMPLETION-CANDIDATES} (default: ${DEFAULT-VALUE}).")
    private Mode mode;

    @CommandLine.Option(names = "--lod-search-depth", paramLabel = "<0..n|all>", defaultValue = "0",
            description = "Levels of sub-features to search for matching LoDs (default: ${DEFAULT-VALUE}).")
    private String depth;

    private Integer searchDepth;

    public LodFilter getLodFilter() {
        return new LodFilter()
                .setLods(lods != null ? new LinkedHashSet<>(Arrays.asList(lods)) : null)
                .setSearchDepth(searchDepth)
                .setMode(mode == Mode.and ? LodFilterMode.AND : LodFilterMode.OR);
    }

    public org.citydb.operation.exporter.options.LodOptions getLodExportOptions() {
        return new org.citydb.operation.exporter.options.LodOptions()
                .setLods(lods != null ? new LinkedHashSet<>(Arrays.asList(lods)) : null)
                .setMode(switch (mode) {
                    case and, or -> LodMode.KEEP;
                    case minimum -> LodMode.MINIMUM;
                    case maximum -> LodMode.MAXIMUM;
                });
    }

    @Override
    public void preprocess(CommandLine commandLine) throws Exception {
        if (depth != null && !depth.equalsIgnoreCase("all")) {
            try {
                searchDepth = Integer.parseInt(depth);
                if (searchDepth < 0) {
                    throw new CommandLine.ParameterException(commandLine,
                            "Error: The LoD search depth must be a non-negative integer but was '" + searchDepth + "'");
                }
            } catch (NumberFormatException e) {
                throw new CommandLine.ParameterException(commandLine,
                        "Error: The LoD search depth must be an integer or 'all' but was '" + searchDepth + "'");
            }
        }
    }
}
