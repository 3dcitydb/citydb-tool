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
