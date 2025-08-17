/*
 * citydb-tool - Command-line tool for the 3D City Database
 * https://www.3dcitydb.org/
 *
 * Copyright 2022-2025
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
