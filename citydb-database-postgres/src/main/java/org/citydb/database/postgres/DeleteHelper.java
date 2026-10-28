/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.database.postgres;

import org.citydb.database.adapter.DatabaseAdapter;
import org.citydb.database.util.SqlHelper;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Collection;

public class DeleteHelper extends org.citydb.database.util.DeleteHelper {
    private final SqlHelper helper;
    private final String schema;
    private PreparedStatement deleteFeatureStmt;
    private PreparedStatement deleteFeaturesStmt;
    private PreparedStatement terminateFeatureStmt;
    private PreparedStatement terminateFeaturesStmt;

    DeleteHelper(Connection connection, DatabaseAdapter adapter) {
        super(connection);
        helper = adapter.getSchemaAdapter().getSqlHelper();
        schema = adapter.getConnectionDetails().getSchema();
    }

    private PreparedStatement prepareDeleteStatement() throws SQLException {
        return connection.prepareCall("{call citydb_pkg.delete_feature(?, ?)}");
    }

    private PreparedStatement prepareTerminateStatement() throws SQLException {
        return connection.prepareCall("{call citydb_pkg.terminate_feature(?, ?, ?, ?)}");
    }

    @Override
    public void deleteFeature(long id) throws SQLException {
        if (deleteFeatureStmt == null) {
            deleteFeatureStmt = prepareDeleteStatement();
        }

        deleteFeatureStmt.setLong(1, id);
        deleteFeatureStmt.setString(2, schema);
        deleteFeatureStmt.execute();
    }

    @Override
    public void deleteFeatures(Collection<Long> ids) throws SQLException {
        if (deleteFeaturesStmt == null) {
            deleteFeaturesStmt = prepareDeleteStatement();
        }

        helper.setLongArrayOrNull(deleteFeaturesStmt, 1, ids);
        deleteFeaturesStmt.setString(2, schema);
        deleteFeaturesStmt.execute();
    }

    @Override
    public void terminateFeature(long id, String metadata, boolean cascade) throws SQLException {
        if (terminateFeatureStmt == null) {
            terminateFeatureStmt = prepareTerminateStatement();
        }

        terminateFeatureStmt.setLong(1, id);
        terminateFeatureStmt.setString(2, schema);
        helper.setJsonOrNull(terminateFeatureStmt, 3, metadata);
        terminateFeatureStmt.setBoolean(4, cascade);
        terminateFeatureStmt.execute();
    }

    @Override
    public void terminateFeatures(Collection<Long> ids, String metadata, boolean cascade) throws SQLException {
        if (terminateFeaturesStmt == null) {
            terminateFeaturesStmt = prepareTerminateStatement();
        }

        helper.setLongArrayOrNull(terminateFeaturesStmt, 1, ids);
        terminateFeaturesStmt.setString(2, schema);
        helper.setJsonOrNull(terminateFeaturesStmt, 3, metadata);
        terminateFeaturesStmt.setBoolean(4, cascade);
        terminateFeaturesStmt.execute();
    }

    @Override
    public void close() throws SQLException {
        SQLException exception = null;
        for (PreparedStatement stmt : new PreparedStatement[]{
                deleteFeatureStmt, deleteFeaturesStmt, terminateFeatureStmt, terminateFeaturesStmt}) {
            if (stmt != null) {
                try {
                    stmt.close();
                } catch (SQLException e) {
                    if (exception == null) {
                        exception = e;
                    } else {
                        exception.addSuppressed(e);
                    }
                }
            }
        }

        deleteFeatureStmt = null;
        deleteFeaturesStmt = null;
        terminateFeatureStmt = null;
        terminateFeaturesStmt = null;

        if (exception != null) {
            throw exception;
        }
    }
}
