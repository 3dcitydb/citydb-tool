/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.cli.common;

import org.citydb.config.common.SrsReference;
import org.citydb.query.filter.Filter;
import org.citydb.query.filter.encoding.FilterParseException;
import org.citydb.query.filter.operation.Operators;
import org.citydb.query.filter.operation.SqlExpression;
import picocli.CommandLine;

public class FilterOptions {
    @CommandLine.Option(names = {"-f", "--filter"}, paramLabel = "<cql2-text>",
            description = "Filter to apply when retrieving features. Use the extended CQL2 " +
                    "filtering language of the 3DCityDB.")
    private String textFilter;

    @CommandLine.Option(names = {"--filter-crs"}, paramLabel = "<crs>",
            description = "SRID or identifier of the CRS to use for geometries in the filter expression.")
    private String filterCrs;

    @CommandLine.Option(names = {"--sql-filter"}, paramLabel = "<sql>",
            description = "SQL query expression to use as filter.")
    private String sqlFilter;

    public Filter getFilter() throws FilterParseException {
        Filter filter = null;
        if (textFilter != null) {
            filter = Filter.ofText(textFilter);
        }

        if (sqlFilter != null) {
            SqlExpression sqlExpression = SqlExpression.of(sqlFilter);
            filter = filter != null ?
                    Filter.of(Operators.and(filter.getExpression(), sqlExpression)) :
                    Filter.of(sqlExpression);
        }

        return filter;
    }

    public SrsReference getFilterCrs() {
        if (filterCrs != null) {
            SrsReference reference = new SrsReference();
            try {
                return reference.setSRID(Integer.parseInt(filterCrs));
            } catch (NumberFormatException e) {
                return reference.setIdentifier(filterCrs);
            }
        } else {
            return null;
        }
    }
}
