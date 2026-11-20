/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
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
            description = "Export geometries with a matching LoD.")
    private String[] lods;

    @CommandLine.Option(names = "--lod-mode", defaultValue = "or",
            description = "LoD filter mode: ${COMPLETION-CANDIDATES} (default: ${DEFAULT-VALUE}).")
    private Mode mode;

    public LodFilter getLodFilter() {
        return new LodFilter()
                .setLods(lods != null ? new LinkedHashSet<>(Arrays.asList(lods)) : null)
                .setMode(mode == Mode.and ? LodFilterMode.AND : LodFilterMode.OR);
    }

    public org.citydb.operation.exporter.options.LodOptions getExportLodOptions() {
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
        if (lods == null && (mode == Mode.or || mode == Mode.and)) {
            throw new CommandLine.ParameterException(commandLine,
                    "Error: Missing required option --lod for LoD filter mode '" + mode + "'.");
        }
    }
}
