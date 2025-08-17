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

package org.citydb.cli.exporter.options;

import org.citydb.cli.common.Option;
import org.citydb.query.filter.encoding.FilterParseException;
import org.citydb.query.sorting.SortOrder;
import org.citydb.query.sorting.Sorting;
import picocli.CommandLine;

public class SortingOptions implements Option {
    @CommandLine.Option(names = {"-s", "--sort-by"}, split = ",", paramLabel = "<property[+|-]>",
            description = "Properties and sort orders for sorting features.")
    private String[] sortBy;

    public Sorting getSorting() throws FilterParseException {
        if (sortBy != null) {
            Sorting sorting = new Sorting();
            for (String sortToken : sortBy) {
                if (sortToken.endsWith("+")) {
                    sorting.addSortBy(sortToken.substring(0, sortToken.length() - 1), SortOrder.ASC);
                } else if (sortToken.endsWith("-")) {
                    sorting.addSortBy(sortToken.substring(0, sortToken.length() - 1), SortOrder.DESC);
                } else {
                    sorting.addSortBy(sortToken, SortOrder.ASC);
                }
            }

            return sorting;
        } else {
            return null;
        }
    }
}
