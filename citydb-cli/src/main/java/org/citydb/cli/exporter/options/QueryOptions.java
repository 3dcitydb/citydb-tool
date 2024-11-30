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

package org.citydb.cli.exporter.options;

import org.citydb.cli.common.*;
import org.citydb.query.Query;
import org.citydb.query.filter.Filter;
import org.citydb.query.filter.encoding.FilterParseException;
import org.citydb.query.util.QueryHelper;
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
        Query query = new Query();
        if (typeNameOptions != null) {
            query.setFeatureTypes(typeNameOptions.getTypeNames());
        }

        if (filterOptions != null) {
            query.setFilter(filterOptions.getFilter());
            query.setFilterSrs(filterOptions.getFilterCrs());
        } else {
            query.setFilter(Filter.of(QueryHelper.terminationDateIsNull()));
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
