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
