/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.cli.common;

import org.citydb.io.citygml.reader.options.LodMode;
import org.citydb.io.citygml.reader.options.LodOptions;
import picocli.CommandLine;

import java.util.LinkedHashSet;
import java.util.List;

public class LodFilterOptions implements Option {
    enum Mode {keep, remove, minimum, maximum}

    @CommandLine.Option(names = {"-l", "--lod"}, split = ",", paramLabel = "<lod>",
            description = "Process geometries with a matching LoD.")
    private List<Integer> lods;

    @CommandLine.Option(names = "--lod-mode", paramLabel = "<mode>",
            description = "LoD filter mode: ${COMPLETION-CANDIDATES} (default: keep).")
    private Mode mode;

    public LodOptions toLodOptions(LodOptions options) {
        if (options == null) {
            options = new LodOptions();
        }

        if (lods != null) {
            options.setLods(new LinkedHashSet<>(lods));
        }

        if (mode != null) {
            options.setMode(switch (mode) {
                case keep -> LodMode.KEEP;
                case remove -> LodMode.REMOVE;
                case minimum -> LodMode.MINIMUM;
                case maximum -> LodMode.MAXIMUM;
            });
        }

        return options;
    }

    @Override
    public void preprocess(CommandLine commandLine) throws Exception {
        if (lods == null && (mode == null || mode == Mode.keep || mode == Mode.remove)) {
            throw new CommandLine.ParameterException(commandLine,
                    "Error: Missing required option --lod for LoD filter mode '"
                            + (mode != null ? mode : Mode.keep) + "'.");
        }

        if (lods != null) {
            for (int lod : lods) {
                if (lod < 0 || lod > 4) {
                    throw new CommandLine.ParameterException(commandLine,
                            "Error: The LoD must be an integer between 0 and 4 but was '" + lod + "'.");
                }
            }
        }
    }
}
