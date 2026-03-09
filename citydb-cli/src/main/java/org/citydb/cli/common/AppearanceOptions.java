/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.cli.common;

import picocli.CommandLine;

import java.util.Set;
import java.util.stream.Collectors;

public class AppearanceOptions implements Option {
    @CommandLine.Option(names = "--no-appearances",
            description = "Do not process appearances.")
    private Boolean noAppearances;

    @CommandLine.Option(names = {"-a", "--appearance-theme"}, split = ",", paramLabel = "<theme>",
            description = "Process appearances with a matching theme. Use 'none' for the null theme.")
    private Set<String> themes;

    public boolean isProcessAppearances() {
        return noAppearances == null || !noAppearances;
    }

    public Set<String> getThemes() {
        return themes;
    }

    @Override
    public void preprocess(CommandLine commandLine) throws Exception {
        if (themes != null) {
            themes = themes.stream()
                    .map(theme -> "none".equalsIgnoreCase(theme) ? null : theme)
                    .collect(Collectors.toSet());
        }
    }
}
