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
import java.util.List;
import java.util.Set;

public class DeleteHelper extends org.citydb.database.util.DeleteHelper {
    private final SqlHelper helper;
    private final String schema;
    private PreparedStatement deleteFeatureStmt;
    private PreparedStatement terminateFeatureStmt;

    DeleteHelper(Connection connection, DatabaseAdapter adapter) {
        super(connection);
        helper = adapter.getSchemaAdapter().getSqlHelper();
        schema = adapter.getConnectionDetails().getSchema();
    }

    @Override
    public boolean deleteFeature(long id) throws SQLException {
        return !deleteFeatures(List.of(id)).isEmpty();
    }

    @Override
    public Set<Long> deleteFeatures(Collection<Long> ids) throws SQLException {
        if (deleteFeatureStmt == null) {
            deleteFeatureStmt = connection.prepareStatement("select * from citydb_pkg.delete_feature(?, ?)");
        }

        helper.setLongArrayOrNull(deleteFeatureStmt, 1, ids);
        deleteFeatureStmt.setString(2, schema);

        return getIds(deleteFeatureStmt);
    }

    @Override
    public boolean terminateFeature(long id, String metadata) throws SQLException {
        return !terminateFeatures(List.of(id), metadata).isEmpty();
    }

    @Override
    public Set<Long> terminateFeatures(Collection<Long> ids, String metadata) throws SQLException {
        if (terminateFeatureStmt == null) {
            terminateFeatureStmt = connection.prepareStatement("select * from citydb_pkg.terminate_feature(?, ?, ?)");
        }

        helper.setLongArrayOrNull(terminateFeatureStmt, 1, ids);
        terminateFeatureStmt.setString(2, schema);
        helper.setJsonOrNull(terminateFeatureStmt, 3, metadata);

        return getIds(terminateFeatureStmt);
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
        try (PreparedStatement deleteFeaturesStmt = this.deleteFeatureStmt;
             PreparedStatement terminateFeaturesStmt = this.terminateFeatureStmt) {
        }
    }
}
