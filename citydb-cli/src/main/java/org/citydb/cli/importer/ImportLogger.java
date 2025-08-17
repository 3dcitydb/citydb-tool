/*
 * citydb-tool - Command-line tool for the 3D City Database
 * https://www.3dcitydb.org/
 *
 * Copyright 2022-2025
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

package org.citydb.cli.importer;

import org.citydb.cli.util.FeatureStatistics;
import org.citydb.database.adapter.DatabaseAdapter;
import org.citydb.model.feature.Feature;
import org.citydb.operation.importer.util.ImportLogEntry;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ImportLogger implements org.citydb.operation.importer.util.ImportLogger {
    private final boolean preview;
    private final DatabaseAdapter adapter;
    private final FeatureStatistics statistics;
    private final Map<String, FeatureStatistics> hierarchies = new ConcurrentHashMap<>();

    ImportLogger(boolean preview, DatabaseAdapter adapter) {
        this.preview = preview;
        this.adapter = adapter;
        statistics = new FeatureStatistics(adapter);
    }

    FeatureStatistics getStatistics() {
        return statistics;
    }

    @Override
    public void log(ImportLogEntry logEntry) {
        FeatureStatistics hierarchy = hierarchies.remove(logEntry.getObjectId());
        if (hierarchy != null && (preview || logEntry.isCommitted())) {
            statistics.merge(hierarchy);
        }
    }

    void add(Feature feature) {
        hierarchies.computeIfAbsent(feature.getOrCreateObjectId(), k -> new FeatureStatistics(adapter))
                .add(feature);
    }
}
