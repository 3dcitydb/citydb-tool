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

package org.citydb.operation.deleter;

import org.citydb.database.adapter.DatabaseAdapter;
import org.citydb.database.schema.Table;
import org.citydb.operation.deleter.common.DatabaseDeleter;
import org.citydb.operation.deleter.feature.FeatureDeleter;
import org.citydb.operation.deleter.util.DeleteLogEntry;
import org.citydb.operation.deleter.util.DeleteLogger;
import org.citydb.operation.deleter.util.TableHelper;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class DeleteHelper {
    private final DatabaseAdapter adapter;
    private final Connection connection;
    private final DeleteOptions options;
    private final DeleteLogger logger;
    private final TableHelper tableHelper;
    private final List<DeleteLogEntry> logEntries = new ArrayList<>();
    private final Deleter.TransactionMode transactionMode;
    private final int batchSize;

    private boolean shouldCommit;

    DeleteHelper(DatabaseAdapter adapter, Connection connection, DeleteOptions options, DeleteLogger logger,
                 Deleter.TransactionMode transactionMode) {
        this.adapter = adapter;
        this.connection = connection;
        this.options = options;
        this.logger = logger;
        this.transactionMode = transactionMode;

        tableHelper = new TableHelper(this);
        batchSize = options.getBatchSize() > 0 ?
                options.getBatchSize() :
                DeleteOptions.DEFAULT_BATCH_SIZE;
    }

    public DatabaseAdapter getAdapter() {
        return adapter;
    }

    public Connection getConnection() {
        return connection;
    }

    public DeleteOptions getOptions() {
        return options;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public TableHelper getTableHelper() {
        return tableHelper;
    }

    void deleteFeature(long id) throws DeleteException {
        try {
            tableHelper.getOrCreateDeleter(FeatureDeleter.class).deleteFeature(id);

            if (logger != null) {
                logEntries.add(DeleteLogEntry.of(Table.FEATURE, id));
            }

            executeBatch(false, transactionMode == Deleter.TransactionMode.AUTO_COMMIT);
        } catch (Exception e) {
            throw new DeleteException("Failed to delete feature (ID: " + id + ").", e);
        }
    }

    public void executeBatch(DatabaseDeleter deleter) throws DeleteException, SQLException {
        deleter.executeBatch();
        shouldCommit = true;
    }

    void executeBatch(boolean force, boolean commit) throws DeleteException, SQLException {
        if (force || shouldCommit) {
            try {
                for (DatabaseDeleter deleter : tableHelper.getDeleters()) {
                    deleter.executeBatch();
                }

                if (commit) {
                    connection.commit();
                } else if (transactionMode == Deleter.TransactionMode.AUTO_ROLLBACK) {
                    connection.rollback();
                }

                updateDeleteLog(commit);
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                shouldCommit = false;
            }
        }
    }

    private void updateDeleteLog(boolean commit) throws DeleteException {
        if (logger != null && !logEntries.isEmpty()) {
            try {
                for (DeleteLogEntry logEntry : logEntries) {
                    logger.log(logEntry.setCommitted(commit));
                }
            } finally {
                logEntries.clear();
            }
        }
    }

    void close() throws DeleteException, SQLException {
        updateDeleteLog(false);
        for (DatabaseDeleter deleter : tableHelper.getDeleters()) {
            deleter.close();
        }
    }
}
