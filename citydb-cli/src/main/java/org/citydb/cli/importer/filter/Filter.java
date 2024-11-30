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

package org.citydb.cli.importer.filter;

import org.citydb.config.common.CountLimit;
import org.citydb.database.adapter.DatabaseAdapter;
import org.citydb.io.reader.filter.FilterException;
import org.citydb.io.reader.filter.FilterPredicate;
import org.citydb.io.reader.options.FilterOptions;
import org.citydb.model.feature.Feature;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class Filter implements org.citydb.io.reader.filter.Filter {
    private final List<FilterPredicate> predicates;
    private final long startIndex;
    private final long limit;
    private final boolean useCountLimit;

    private long currentIndex;
    private long count;

    private Filter(List<FilterPredicate> predicates, long startIndex, long limit) {
        this.predicates = predicates;
        this.startIndex = startIndex;
        this.limit = limit;
        useCountLimit = startIndex > 0 || limit < Long.MAX_VALUE;
    }

    public static Filter of(FilterOptions options, DatabaseAdapter adapter) throws FilterException {
        Objects.requireNonNull(options, "The filter options must not be null.");
        Objects.requireNonNull(adapter, "The database adapter must not be null.");

        List<FilterPredicate> predicates = new ArrayList<>();
        if (options.hasFeatureTypes()) {
            predicates.add(FeatureTypeFilterPredicate.of(options.getFeatureTypes(),
                    adapter.getSchemaAdapter().getSchemaMapping()));
        }

        if (options.hasIds()) {
            predicates.add(feature -> feature.getObjectId()
                    .map(options.getIds()::contains)
                    .orElse(false));
        }

        options.getBbox().ifPresent(bbox ->
                predicates.add(BboxPredicate.of(bbox, options.getBboxMode(), adapter)));

        long startIndex = options.getCountLimit().flatMap(CountLimit::getStartIndex).orElse(0L);
        long limit = options.getCountLimit().flatMap(CountLimit::getLimit).orElse(Long.MAX_VALUE);

        return new Filter(predicates, startIndex, limit);
    }

    @Override
    public boolean needsSequentialProcessing() {
        return useCountLimit;
    }

    @Override
    public Result test(Feature feature) throws FilterException {
        if (!predicates.isEmpty()) {
            for (FilterPredicate predicate : predicates) {
                if (!predicate.test(feature)) {
                    return Result.SKIP;
                }
            }
        }

        if (useCountLimit) {
            if (currentIndex++ < startIndex) {
                return Result.SKIP;
            } else if (++count > limit) {
                return Result.STOP;
            }
        }

        return Result.ACCEPT;
    }
}
