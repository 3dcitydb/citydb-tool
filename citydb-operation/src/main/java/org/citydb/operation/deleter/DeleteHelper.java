/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
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
    private final boolean ownsConnection;
    private final int batchSize;
    private final int commitAfter;

    private int commitCounter;

    DeleteHelper(DatabaseAdapter adapter, Connection connection, boolean ownsConnection, DeleteOptions options,
                 DeleteLogger logger, Deleter.TransactionMode transactionMode) {
        this.adapter = adapter;
        this.connection = connection;
        this.ownsConnection = ownsConnection;
        this.options = options;
        this.logger = logger;
        this.transactionMode = ownsConnection ? transactionMode : null;

        tableHelper = new TableHelper(this);
        batchSize = adapter.getSchemaAdapter().getDefaultBatchSize();
        commitAfter = this.transactionMode == Deleter.TransactionMode.COMMIT_BATCHES
                || this.transactionMode == Deleter.TransactionMode.ROLLBACK
                ? options.getCommitAfter() > 0 ? options.getCommitAfter() : DeleteOptions.DEFAULT_COMMIT_AFTER
                : 0;
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

            executeBatch(false, transactionMode == Deleter.TransactionMode.COMMIT_BATCHES);
        } catch (Exception e) {
            throw new DeleteException("Failed to delete feature (ID: " + id + ").", e);
        }
    }

    void executeBatch(boolean force, boolean commit) throws DeleteException, SQLException {
        if (force || (commitAfter > 0 && ++commitCounter == commitAfter)) {
            try {
                for (DatabaseDeleter deleter : tableHelper.getDeleters()) {
                    deleter.executeBatch();
                }

                if (commit && ownsConnection) {
                    connection.commit();
                } else if (transactionMode == Deleter.TransactionMode.ROLLBACK) {
                    connection.rollback();
                }

                updateDeleteLog(commit && ownsConnection);
            } catch (SQLException e) {
                if (ownsConnection) {
                    connection.rollback();
                }

                throw e;
            } finally {
                commitCounter = 0;
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
