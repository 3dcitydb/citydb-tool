/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.cli.importer.options;

import org.citydb.cli.common.Option;
import org.citydb.cli.common.TypeNameOptions;
import picocli.CommandLine;

public class FilterOptions implements Option {
    @CommandLine.ArgGroup(exclusive = false)
    private TypeNameOptions typeNameOptions;

    @CommandLine.ArgGroup(exclusive = false)
    private IdOptions idOptions;

    @CommandLine.ArgGroup(exclusive = false)
    private BboxOptions bboxOptions;

    @CommandLine.ArgGroup(exclusive = false)
    private CountLimitOptions countLimitOptions;

    public org.citydb.io.reader.options.FilterOptions getImportFilterOptions() {
        org.citydb.io.reader.options.FilterOptions options = new org.citydb.io.reader.options.FilterOptions();
        if (typeNameOptions != null) {
            options.setFeatureTypes(typeNameOptions.getTypeNames());
        }

        if (idOptions != null) {
            options.setIds(idOptions.getIds());
        }

        if (bboxOptions != null) {
            options.setBbox(bboxOptions.getEnvelope())
                    .setBboxMode(bboxOptions.getMode());
        }

        if (countLimitOptions != null) {
            options.setCountLimit(countLimitOptions.getCountLimit());
        }

        return options;
    }

    @Override
    public void preprocess(CommandLine commandLine) throws Exception {
        if (bboxOptions != null) {
            bboxOptions.preprocess(commandLine);
        }

        if (countLimitOptions != null) {
            countLimitOptions.preprocess(commandLine);
        }
    }
}
