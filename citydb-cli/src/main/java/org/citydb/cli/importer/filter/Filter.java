/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.cli.importer.filter;

import org.citydb.config.common.CountLimit;
import org.citydb.core.tuple.SimplePair;
import org.citydb.database.adapter.DatabaseAdapter;
import org.citydb.io.reader.filter.FilterException;
import org.citydb.io.reader.filter.FilterPredicate;
import org.citydb.io.reader.options.FilterOptions;
import org.citydb.model.feature.Feature;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class Filter implements org.citydb.io.reader.filter.Filter {
    public static final Filter ACCEPT_ALL = new Filter() {
        @Override
        public Result test(Feature feature) {
            return Result.ACCEPT;
        }
    };

    private final List<FilterPredicate> predicates;
    private final long startIndex;
    private final long limit;
    private final boolean useCountLimit;

    private long currentIndex;
    private long count;
    private SimplePair<Long> state;

    private Filter() {
        this(null, 0, Long.MAX_VALUE);
    }

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

        return !predicates.isEmpty() || startIndex > 0 || limit < Long.MAX_VALUE ?
                new Filter(predicates, startIndex, limit) :
                Filter.ACCEPT_ALL;
    }

    public boolean isCountWithinLimit() {
        return !useCountLimit || count < limit;
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

    public void saveState() {
        state = SimplePair.of(currentIndex, count);
    }

    public void restoreState() {
        if (state != null) {
            currentIndex = state.first();
            count = state.second();
            state = null;
        }
    }
}
