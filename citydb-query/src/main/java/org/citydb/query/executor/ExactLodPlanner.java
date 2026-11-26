/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.query.executor;

import org.citydb.database.adapter.DatabaseAdapter;
import org.citydb.database.util.StatementOptions;
import org.citydb.query.lod.LodFilter;
import org.citydb.query.lod.LodPlan;
import org.citydb.query.lod.LodSearch;
import org.citydb.sqlbuilder.function.Function;
import org.citydb.sqlbuilder.query.Select;
import org.citydb.sqlbuilder.schema.Table;
import org.citydb.sqlbuilder.schema.WildcardColumn;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

class ExactLodPlanner implements LodPlanner {
    private final Long threshold;

    ExactLodPlanner(Long threshold) {
        this.threshold = threshold;
    }

    @Override
    public Optional<LodPlan> plan(LodFilter lodFilter, DatabaseAdapter adapter, Connection connection) throws SQLException {
        long max = threshold != null ? threshold : adapter.getSchemaAdapter().getLodHelper().getSearchThreshold();
        Map<Set<String>, LodSearch> searches = new HashMap<>();
        for (Set<String> group : lodFilter.getGroups()) {
            Select count = Select.newInstance()
                    .select(Function.of("count", WildcardColumn.newInstance()))
                    .from(Table.of(adapter.getSchemaAdapter().getLodHelper().getBottomUpQuery(group)
                            .fetch(max + 1)));

            try (PreparedStatement stmt = adapter.getSchemaAdapter().getSqlHelper()
                    .prepareStatement(count, connection, StatementOptions.defaults().fetchSize(0));
                 ResultSet rs = stmt.executeQuery()) {
                searches.put(group, rs.next() && rs.getLong(1) <= max
                        ? LodSearch.BOTTOM_UP
                        : LodSearch.TOP_DOWN);
            }
        }

        return Optional.of(LodPlan.of(searches));
    }
}
