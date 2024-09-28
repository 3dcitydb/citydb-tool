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
