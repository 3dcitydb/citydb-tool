/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.cli.importer.options;

import picocli.CommandLine;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class IdOptions {
    @CommandLine.Option(names = {"-i", "--id"}, split = ",", paramLabel = "<id>",
            description = "Identifiers of the features to process.")
    private String[] ids;

    public Set<String> getIds() {
        return new HashSet<>(Arrays.asList(ids));
    }
}
