/*
 * citydb-tool - Command-line tool for the 3D City Database
 * https://www.3dcitydb.org/
 *
 * Copyright 2022-2023
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

package org.citydb.operation.importer;

import org.citydb.core.file.FileLocator;
import org.citydb.database.adapter.DatabaseAdapter;
import org.citydb.database.schema.DataTypeHelper;
import org.citydb.database.schema.NamespaceHelper;
import org.citydb.database.schema.ObjectClassHelper;
import org.citydb.database.schema.Table;
import org.citydb.model.common.ExternalFile;
import org.citydb.model.common.Visitable;
import org.citydb.model.feature.Feature;
import org.citydb.model.feature.FeatureDescriptor;
import org.citydb.operation.importer.common.DatabaseImporter;
import org.citydb.operation.importer.feature.FeatureImporter;
import org.citydb.operation.importer.reference.CacheType;
import org.citydb.operation.importer.reference.ReferenceCache;
import org.citydb.operation.importer.reference.ReferenceManager;
import org.citydb.operation.importer.util.*;
import org.citydb.operation.util.FeatureStatistics;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class ImportHelper {
    private final DatabaseAdapter adapter;
    private final ReferenceManager referenceManager;
    private final ImportLogger logger;
    private final StatisticsConsumer statisticsConsumer;
    private final Connection connection;
    private final DataTypeHelper dataTypeHelper;
    private final NamespaceHelper namespaceHelper;
    private final ObjectClassHelper objectClassHelper;
    private final TableHelper tableHelper;
    private final SequenceHelper sequenceHelper;
    private final FeatureStatistics statistics;
    private final Map<CacheType, ReferenceCache> caches = new EnumMap<>(CacheType.class);
    private final List<ImportLogEntry> logEntries = new ArrayList<>();
    private final int batchSize;
    private final boolean autoCommit;

    private SequenceValues sequenceValues;
    private int batchCounter;

    ImportHelper(DatabaseAdapter adapter, ImportOptions options, ReferenceManager referenceManager,
                 ImportLogger logger, StatisticsConsumer statisticsConsumer, boolean autoCommit) throws SQLException {
        this.adapter = adapter;
        this.referenceManager = referenceManager;
        this.logger = logger;
        this.statisticsConsumer = statisticsConsumer;
        this.autoCommit = autoCommit;

        connection = adapter.getPool().getConnection(false);
        dataTypeHelper = adapter.getSchemaAdapter().getDataTypeHelper();
        namespaceHelper = adapter.getSchemaAdapter().getNamespaceHelper();
        objectClassHelper = adapter.getSchemaAdapter().getObjectClassHelper();
        tableHelper = new TableHelper(this);
        sequenceHelper = new SequenceHelper(this);
        statistics = new FeatureStatistics(objectClassHelper, namespaceHelper);
        batchSize = Math.min(options.getBatchSize(), adapter.getSchemaAdapter().getMaximumBatchSize());
    }

    public DatabaseAdapter getAdapter() {
        return adapter;
    }

    public DataTypeHelper getDataTypeHelper() {
        return dataTypeHelper;
    }

    public NamespaceHelper getNamespaceHelper() {
        return namespaceHelper;
    }

    public ObjectClassHelper getObjectClassHelper() {
        return objectClassHelper;
    }

    public Connection getConnection() {
        return connection;
    }

    public TableHelper getTableHelper() {
        return tableHelper;
    }

    public SequenceValues getSequenceValues() {
        return sequenceValues;
    }

    public ReferenceCache getOrCreateReferenceCache(CacheType type) {
        return caches.computeIfAbsent(type, v -> new ReferenceCache(type));
    }

    public FileLocator getFileLocator(ExternalFile file) {
        if (file != null) {
            return file.getPath().map(FileLocator::of)
                    .orElseGet(() -> FileLocator.of(file.getFileLocation()));
        }

        return null;
    }

    FeatureDescriptor importFeature(Feature feature) throws ImportException {
        try {
            generateSequenceValues(feature);
            FeatureDescriptor descriptor = tableHelper.getOrCreateImporter(FeatureImporter.class).doImport(feature);

            if (statisticsConsumer != null) {
                statistics.add(feature);
            }

            if (logger != null) {
                logEntries.add(ImportLogEntry.of(feature, descriptor));
            }

            executeBatch(false, autoCommit);
            return descriptor;
        } catch (Exception e) {
            throw new ImportException("Failed to import feature.", e);
        }
    }

    private void generateSequenceValues(Visitable visitable) throws SQLException {
        sequenceValues = sequenceHelper.nextSequenceValues(visitable);
    }

    void executeBatch(boolean force, boolean commit) throws ImportException, SQLException {
        if (force || ++batchCounter == batchSize) {
            try {
                if (batchCounter > 0) {
                    for (Table table : tableHelper.getCommitOrder()) {
                        for (DatabaseImporter importer : tableHelper.getImporters(table)) {
                            importer.executeBatch();
                        }
                    }
                }

                for (ReferenceCache cache : caches.values()) {
                    referenceManager.storeReferences(cache);
                }

                if (commit) {
                    connection.commit();
                    updateImportLog();
                }

                updateStatistics(commit);
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                batchCounter = 0;
            }
        }
    }

    private void updateImportLog() throws ImportException {
        if (logger != null && !logEntries.isEmpty()) {
            try {
                for (ImportLogEntry logEntry : logEntries) {
                    logger.log(logEntry);
                }
            } finally {
                logEntries.clear();
            }
        }
    }

    private void updateStatistics(boolean commit) {
        if (statisticsConsumer != null && !statistics.isEmpty()) {
            if ((commit && statisticsConsumer.getMode() == StatisticsConsumer.Mode.COUNT_COMMITTED)
                    || statisticsConsumer.getMode() == StatisticsConsumer.Mode.COUNT_ALL) {
                try {
                    statisticsConsumer.accept(statistics);
                } finally {
                    statistics.clear();
                }
            }
        }
    }

    void close() throws SQLException {
        updateStatistics(false);
        logEntries.clear();
        sequenceHelper.close();

        try {
            tableHelper.close();
        } finally {
            connection.close();
        }
    }
}
