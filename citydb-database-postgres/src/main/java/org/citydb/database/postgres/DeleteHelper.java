/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.database.postgres;

import org.citydb.database.adapter.DatabaseAdapter;
import org.citydb.database.util.SqlHelper;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

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

    @Override
    public boolean deleteFeature(long id) throws SQLException {
        if (deleteFeatureStmt == null) {
            deleteFeatureStmt = prepareDeleteStatement();
        }

        deleteFeatureStmt.setLong(1, id);
        deleteFeatureStmt.setString(2, schema);

        return !getIds(deleteFeatureStmt).isEmpty();
    }

    @Override
    public Set<Long> deleteFeatures(Collection<Long> ids) throws SQLException {
        if (deleteFeaturesStmt == null) {
            deleteFeaturesStmt = prepareDeleteStatement();
        }

        helper.setLongArrayOrNull(deleteFeaturesStmt, 1, ids);
        deleteFeaturesStmt.setString(2, schema);

        return getIds(deleteFeaturesStmt);
    }

    @Override
    public boolean terminateFeature(long id, String metadata, boolean cascade) throws SQLException {
        if (terminateFeatureStmt == null) {
            terminateFeatureStmt = prepareTerminateStatement();
        }

        terminateFeatureStmt.setLong(1, id);
        terminateFeatureStmt.setString(2, schema);
        helper.setJsonOrNull(terminateFeatureStmt, 3, metadata);
        terminateFeatureStmt.setBoolean(4, cascade);

        return !getIds(terminateFeatureStmt).isEmpty();
    }

    @Override
    public Set<Long> terminateFeatures(Collection<Long> ids, String metadata, boolean cascade) throws SQLException {
        if (terminateFeaturesStmt == null) {
            terminateFeaturesStmt = prepareTerminateStatement();
        }

        helper.setLongArrayOrNull(terminateFeaturesStmt, 1, ids);
        terminateFeaturesStmt.setString(2, schema);
        helper.setJsonOrNull(terminateFeaturesStmt, 3, metadata);
        terminateFeaturesStmt.setBoolean(4, cascade);

        return getIds(terminateFeaturesStmt);
    }

    private PreparedStatement prepareDeleteStatement() throws SQLException {
        return connection.prepareStatement("select * from citydb_pkg.delete_feature(?, ?)");
    }

    private PreparedStatement prepareTerminateStatement() throws SQLException {
        return connection.prepareStatement("select * from citydb_pkg.terminate_feature(?, ?, ?, ?)");
    }

    private Set<Long> getIds(PreparedStatement stmt) throws SQLException {
        Set<Long> ids = new HashSet<>();
        try (ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                long id = rs.getLong(1);
                if (!rs.wasNull()) {
                    ids.add(id);
                }
            }
        }

        return ids;
    }

    @Override
    public void close() throws SQLException {
        try (PreparedStatement deleteFeatureStmt = this.deleteFeatureStmt;
             PreparedStatement deleteFeaturesStmt = this.deleteFeaturesStmt;
             PreparedStatement terminateFeatureStmt = this.terminateFeatureStmt;
             PreparedStatement terminateFeaturesStmt = this.terminateFeaturesStmt) {
        }
    }
}
