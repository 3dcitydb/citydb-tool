/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.cli.common;

import org.citydb.model.common.PrefixedName;
import picocli.CommandLine;

import java.util.Arrays;
import java.util.List;

public class TypeNameOptions implements Option {
    @CommandLine.Option(names = {"-t", "--type-name"}, split = ",", paramLabel = "<[prefix:]name>",
            description = "Names of the features to process.")
    private String[] typeNames;

    public List<PrefixedName> getTypeNames() {
        return Arrays.stream(typeNames)
                .map(PrefixedName::of)
                .toList();
    }
}
