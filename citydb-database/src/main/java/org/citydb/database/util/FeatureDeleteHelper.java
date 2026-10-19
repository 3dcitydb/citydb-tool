/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.database.util;

import org.citydb.database.adapter.DatabaseAdapter;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Collection;

public abstract class FeatureDeleteHelper implements AutoCloseable {
    public static final String DELETE_FEATURE_FUNCTION = "citydb_pkg.delete_feature";
    public static final String TERMINATE_FEATURE_FUNCTION = "citydb_pkg.terminate_feature";

    protected final Connection connection;

    protected FeatureDeleteHelper(Connection connection) {
        this.connection = connection;
    }

    public static FeatureDeleteHelper of(Connection connection, DatabaseAdapter adapter) {
        return of(connection, adapter, DELETE_FEATURE_FUNCTION, TERMINATE_FEATURE_FUNCTION);
    }

    public static FeatureDeleteHelper of(Connection connection, DatabaseAdapter adapter, String deleteFunction,
                                         String terminateFunction) {
        return new DefaultFeatureDeleteHelper(connection, adapter, deleteFunction, terminateFunction);
    }

    public abstract void deleteFeatures(Collection<Long> ids) throws SQLException;

    public abstract void terminateFeatures(Collection<Long> ids, String metadata, boolean cascade) throws SQLException;

    @Override
    public abstract void close() throws SQLException;

    private static class DefaultFeatureDeleteHelper extends FeatureDeleteHelper {
        private final SqlHelper helper;
        private final String schema;
        private final String deleteFunction;
        private final String terminateFunction;
        private PreparedStatement deleteStmt;
        private PreparedStatement terminateStmt;

        DefaultFeatureDeleteHelper(Connection connection, DatabaseAdapter adapter, String deleteFunction,
                                   String terminateFunction) {
            super(connection);
            this.deleteFunction = deleteFunction;
            this.terminateFunction = terminateFunction;
            helper = adapter.getSchemaAdapter().getSqlHelper();
            schema = adapter.getConnectionDetails().getSchema();
        }

        @Override
        public void deleteFeatures(Collection<Long> ids) throws SQLException {
            if (deleteStmt == null) {
                deleteStmt = connection.prepareCall("{call " + deleteFunction + "(?, ?)}");
            }

            helper.setLongArrayOrNull(deleteStmt, 1, ids);
            deleteStmt.setString(2, schema);
            deleteStmt.execute();
        }

        @Override
        public void terminateFeatures(Collection<Long> ids, String metadata, boolean cascade) throws SQLException {
            if (terminateStmt == null) {
                terminateStmt = connection.prepareCall("{call " + terminateFunction + "(?, ?, ?, ?)}");
            }

            helper.setLongArrayOrNull(terminateStmt, 1, ids);
            terminateStmt.setString(2, schema);
            helper.setJsonOrNull(terminateStmt, 3, metadata);
            terminateStmt.setBoolean(4, cascade);
            terminateStmt.execute();
        }

        @Override
        public void close() throws SQLException {
            if (deleteStmt != null) {
                deleteStmt.close();
            }

            if (terminateStmt != null) {
                terminateStmt.close();
            }
        }
    }
}
