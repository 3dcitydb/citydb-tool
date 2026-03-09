/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.cli.importer.options;

import org.citydb.cli.common.Option;
import org.citydb.config.common.CountLimit;
import picocli.CommandLine;

public class CountLimitOptions implements Option {
    @CommandLine.Option(names = "--limit", paramLabel = "<count>",
            description = "Maximum number of features to process.")
    private Long limit;

    @CommandLine.Option(names = "--start-index", paramLabel = "<index>",
            description = "Index within the input set from which features are processed.")
    private Long startIndex;

    public CountLimit getCountLimit() {
        return limit != null || startIndex != null ?
                new CountLimit()
                        .setLimit(limit)
                        .setStartIndex(startIndex) :
                null;
    }

    @Override
    public void preprocess(CommandLine commandLine) throws Exception {
        if (limit != null && limit < 0) {
            throw new CommandLine.ParameterException(commandLine,
                    "Error: Count limit must be a non-negative integer but was '" + limit + "'");
        } else if (startIndex != null && startIndex < 0) {
            throw new CommandLine.ParameterException(commandLine,
                    "Error: Start index must be a non-negative integer but was '" + startIndex + "'");
        }
    }
}
