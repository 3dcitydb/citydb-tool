/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.query.executor;

import org.citydb.database.adapter.DatabaseAdapter;
import org.citydb.query.lod.LodFilter;
import org.citydb.query.lod.LodPlan;
import org.citydb.query.lod.LodSearch;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Optional;

@FunctionalInterface
public interface LodPlanner {
    Optional<LodPlan> plan(LodFilter lodFilter, DatabaseAdapter adapter, Connection connection) throws SQLException;

    default LodPlanner orElse(LodPlanner fallback) {
        return (lodFilter, adapter, connection) -> {
            Optional<LodPlan> plan = plan(lodFilter, adapter, connection);
            return plan.isPresent() ? plan : fallback.plan(lodFilter, adapter, connection);
        };
    }

    static LodPlanner defaults() {
        return statistics().orElse(exact());
    }

    static LodPlanner statistics() {
        return new StatisticsLodPlanner(null);
    }

    static LodPlanner statistics(long threshold) {
        return new StatisticsLodPlanner(threshold);
    }

    static LodPlanner exact() {
        return new ExactLodPlanner(null);
    }

    static LodPlanner exact(long threshold) {
        return new ExactLodPlanner(threshold);
    }

    static LodPlanner fixed(LodSearch search) {
        return (lodFilter, adapter, connection) -> Optional.of(LodPlan.of(lodFilter, search));
    }
}
