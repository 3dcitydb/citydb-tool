/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
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
