/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.database.postgres;

import org.citydb.core.concurrent.LazyCheckedInitializer;
import org.citydb.database.adapter.DatabaseAdapter;
import org.citydb.database.util.SqlHelper;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Collection;
import java.util.List;

public class DeleteHelper extends org.citydb.database.util.DeleteHelper {
    private final SqlHelper helper;
    private final String schema;
    private final LazyCheckedInitializer<PreparedStatement, SQLException> deleteFeatureStmt;
    private final LazyCheckedInitializer<PreparedStatement, SQLException> deleteFeaturesStmt;
    private final LazyCheckedInitializer<PreparedStatement, SQLException> terminateFeatureStmt;
    private final LazyCheckedInitializer<PreparedStatement, SQLException> terminateFeaturesStmt;

    DeleteHelper(Connection connection, DatabaseAdapter adapter) {
        super(connection);
        helper = adapter.getSchemaAdapter().getSqlHelper();
        schema = adapter.getConnectionDetails().getSchema();
        deleteFeatureStmt = LazyCheckedInitializer.of(this::prepareDeleteStatement);
        deleteFeaturesStmt = LazyCheckedInitializer.of(this::prepareDeleteStatement);
        terminateFeatureStmt = LazyCheckedInitializer.of(this::prepareTerminateStatement);
        terminateFeaturesStmt = LazyCheckedInitializer.of(this::prepareTerminateStatement);
    }

    private PreparedStatement prepareDeleteStatement() throws SQLException {
        return connection.prepareCall("{call citydb_pkg.delete_feature(?, ?)}");
    }

    private PreparedStatement prepareTerminateStatement() throws SQLException {
        return connection.prepareCall("{call citydb_pkg.terminate_feature(?, ?, ?, ?)}");
    }

    @Override
    public void deleteFeature(long id) throws SQLException {
        PreparedStatement stmt = deleteFeatureStmt.get();
        stmt.setLong(1, id);
        stmt.setString(2, schema);
        stmt.execute();
    }

    @Override
    public void deleteFeatures(Collection<Long> ids) throws SQLException {
        PreparedStatement stmt = deleteFeaturesStmt.get();
        helper.setLongArrayOrNull(stmt, 1, ids);
        stmt.setString(2, schema);
        stmt.execute();
    }

    @Override
    public void terminateFeature(long id, String metadata, boolean cascade) throws SQLException {
        PreparedStatement stmt = terminateFeatureStmt.get();
        stmt.setLong(1, id);
        stmt.setString(2, schema);
        helper.setJsonOrNull(stmt, 3, metadata);
        stmt.setBoolean(4, cascade);
        stmt.execute();
    }

    @Override
    public void terminateFeatures(Collection<Long> ids, String metadata, boolean cascade) throws SQLException {
        PreparedStatement stmt = terminateFeaturesStmt.get();
        helper.setLongArrayOrNull(stmt, 1, ids);
        stmt.setString(2, schema);
        helper.setJsonOrNull(stmt, 3, metadata);
        stmt.setBoolean(4, cascade);
        stmt.execute();
    }

    @Override
    public void close() throws SQLException {
        SQLException exception = null;
        for (LazyCheckedInitializer<PreparedStatement, SQLException> stmt : List.of(
                deleteFeatureStmt, deleteFeaturesStmt, terminateFeatureStmt, terminateFeaturesStmt)) {
            if (stmt.isInitialized()) {
                try {
                    stmt.get().close();
                } catch (SQLException e) {
                    exception = e;
                }
            }
        }

        if (exception != null) {
            throw exception;
        }
    }
}
