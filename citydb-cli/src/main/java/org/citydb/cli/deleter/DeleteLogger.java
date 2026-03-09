/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.cli.deleter;

import org.citydb.cli.util.FeatureStatistics;
import org.citydb.database.adapter.DatabaseAdapter;
import org.citydb.operation.deleter.util.DeleteLogEntry;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DeleteLogger implements org.citydb.operation.deleter.util.DeleteLogger {
    private final boolean autoCommit;
    private final FeatureStatistics statistics;
    private final Map<Long, Integer> objectClassIds = new ConcurrentHashMap<>();

    DeleteLogger(boolean autoCommit, DatabaseAdapter adapter) {
        this.autoCommit = autoCommit;
        statistics = new FeatureStatistics(adapter);
    }

    FeatureStatistics getStatistics() {
        return statistics;
    }

    @Override
    public void log(DeleteLogEntry logEntry) {
        Integer objectClassId = objectClassIds.remove(logEntry.getDatabaseId());
        if (objectClassId != null && (!autoCommit || logEntry.isCommitted())) {
            statistics.add(objectClassId);
        }
    }

    void add(long id, int objectClassId) {
        objectClassIds.put(id, objectClassId);
    }
}
