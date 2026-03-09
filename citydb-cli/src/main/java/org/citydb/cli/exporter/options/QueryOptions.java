/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.cli.exporter.options;

import org.citydb.cli.common.*;
import org.citydb.query.Query;
import org.citydb.query.filter.encoding.FilterParseException;
import picocli.CommandLine;

public class QueryOptions implements Option {
    @CommandLine.ArgGroup(exclusive = false)
    private TypeNameOptions typeNameOptions;

    @CommandLine.ArgGroup(exclusive = false)
    private FilterOptions filterOptions;

    @CommandLine.ArgGroup(exclusive = false)
    private SortingOptions sortingOptions;

    @CommandLine.ArgGroup(exclusive = false)
    private CountLimitOptions countLimitOptions;

    @CommandLine.ArgGroup(exclusive = false)
    private LodOptions lodOptions;

    @CommandLine.ArgGroup(exclusive = false)
    private AppearanceOptions appearanceOptions;

    public Query getQuery() throws FilterParseException {
        if (typeNameOptions == null
                && filterOptions == null
                && sortingOptions == null
                && countLimitOptions == null
                && lodOptions == null) {
            return null;
        }

        Query query = new Query();
        if (typeNameOptions != null) {
            query.setFeatureTypes(typeNameOptions.getTypeNames());
        }

        if (filterOptions != null) {
            query.setFilter(filterOptions.getFilter());
            query.setFilterSrs(filterOptions.getFilterCrs());
        }

        if (sortingOptions != null) {
            query.setSorting(sortingOptions.getSorting());
        }

        if (countLimitOptions != null) {
            query.setCountLimit(countLimitOptions.getCountLimit());
        }

        if (lodOptions != null) {
            query.setLodFilter(lodOptions.getLodFilter());
        }

        return query;
    }

    public LodOptions getLodOptions() {
        return lodOptions;
    }

    public AppearanceOptions getAppearanceOptions() {
        return appearanceOptions;
    }

    @Override
    public void preprocess(CommandLine commandLine) throws Exception {
        if (countLimitOptions != null) {
            countLimitOptions.preprocess(commandLine);
        }

        if (lodOptions != null) {
            lodOptions.preprocess(commandLine);
        }

        if (appearanceOptions != null) {
            appearanceOptions.preprocess(commandLine);
        }
    }
}
