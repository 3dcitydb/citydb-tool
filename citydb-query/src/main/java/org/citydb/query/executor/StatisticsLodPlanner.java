/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.query.executor;

import org.citydb.database.adapter.DatabaseAdapter;
import org.citydb.database.util.LodStatistics;
import org.citydb.query.lod.LodFilter;
import org.citydb.query.lod.LodPlan;
import org.citydb.query.lod.LodSearch;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

class StatisticsLodPlanner implements LodPlanner {
    private final Long threshold;

    StatisticsLodPlanner(Long threshold) {
        this.threshold = threshold;
    }

    @Override
    public Optional<LodPlan> plan(LodFilter lodFilter, DatabaseAdapter adapter, Connection connection) throws SQLException {
        Optional<LodStatistics> statistics = adapter.getSchemaAdapter().getLodHelper().getStatistics(connection)
                .filter(candidate -> !candidate.isStale());
        if (statistics.isEmpty()) {
            return Optional.empty();
        }

        long max = threshold != null ? threshold : adapter.getSchemaAdapter().getLodHelper().getSearchThreshold();
        Map<Set<String>, LodSearch> searches = new HashMap<>();
        for (Set<String> group : lodFilter.getGroups()) {
            searches.put(group, statistics.get().estimate(group) <= max
                    ? LodSearch.BOTTOM_UP
                    : LodSearch.TOP_DOWN);
        }

        return Optional.of(LodPlan.of(searches));
    }
}
