/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.cli.deleter.options;

import org.citydb.cli.common.CountLimitOptions;
import org.citydb.cli.common.FilterOptions;
import org.citydb.cli.common.Option;
import org.citydb.cli.common.TypeNameOptions;
import org.citydb.query.Query;
import org.citydb.query.filter.encoding.FilterParseException;
import picocli.CommandLine;

public class QueryOptions implements Option {
    @CommandLine.ArgGroup(exclusive = false)
    private TypeNameOptions typeNameOptions;

    @CommandLine.ArgGroup(exclusive = false)
    private FilterOptions filterOptions;

    @CommandLine.ArgGroup(exclusive = false)
    private CountLimitOptions countLimitOptions;

    public Query getQuery() throws FilterParseException {
        Query query = new Query();
        if (typeNameOptions != null) {
            query.setFeatureTypes(typeNameOptions.getTypeNames());
        }

        if (filterOptions != null) {
            query.setFilter(filterOptions.getFilter());
            query.setFilterSrs(filterOptions.getFilterCrs());
        }

        if (countLimitOptions != null) {
            query.setCountLimit(countLimitOptions.getCountLimit());
        }

        return query;
    }

    @Override
    public void preprocess(CommandLine commandLine) throws Exception {
        if (countLimitOptions != null) {
            countLimitOptions.preprocess(commandLine);
        }
    }
}
