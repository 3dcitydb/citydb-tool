/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.cli.importer;

import org.citydb.cli.util.FeatureStatistics;
import org.citydb.database.adapter.DatabaseAdapter;
import org.citydb.model.feature.Feature;
import org.citydb.operation.importer.util.ImportLogEntry;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ImportLogger implements org.citydb.operation.importer.util.ImportLogger {
    private final boolean preview;
    private final DatabaseAdapter adapter;
    private final FeatureStatistics statistics;
    private final Map<String, FeatureStatistics> hierarchies = new ConcurrentHashMap<>();
    private final Set<String> unprocessed = ConcurrentHashMap.newKeySet();

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
        if (preview || logEntry.isCommitted()) {
            if (hierarchy != null) {
                statistics.merge(hierarchy);
            } else {
                unprocessed.add(logEntry.getObjectId());
            }
        }
    }

    void add(Feature feature) {
        String objectId = feature.getOrCreateObjectId();
        if (unprocessed.remove(objectId)) {
            statistics.add(feature);
        } else {
            hierarchies.computeIfAbsent(objectId, k -> new FeatureStatistics(adapter)).add(feature);
        }
    }
}
