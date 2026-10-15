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

public class FeatureDeleteHelper extends org.citydb.database.util.FeatureDeleteHelper {
    private final SqlHelper helper;
    private final PreparedStatement deleteStmt;
    private final PreparedStatement terminateStmt;
    private final String schema;

    FeatureDeleteHelper(Connection connection, DatabaseAdapter adapter) throws SQLException {
        super(connection);
        helper = adapter.getSchemaAdapter().getSqlHelper();
        schema = adapter.getConnectionDetails().getSchema();

        deleteStmt = connection.prepareCall("{call citydb_pkg.delete_feature(?, ?)}");
        terminateStmt = connection.prepareCall("{call citydb_pkg.terminate_feature(?, ?, ?, ?)}");
    }

    @Override
    public void deleteFeatures(Collection<Long> ids) throws SQLException {
        helper.setLongArrayOrNull(deleteStmt, 1, ids);
        deleteStmt.setString(2, schema);
        deleteStmt.execute();
    }

    @Override
    public void terminateFeatures(Collection<Long> ids, String metadata, boolean cascade) throws SQLException {
        helper.setLongArrayOrNull(terminateStmt, 1, ids);
        terminateStmt.setString(2, schema);
        helper.setJsonOrNull(terminateStmt, 3, metadata);
        terminateStmt.setBoolean(4, cascade);
        terminateStmt.execute();
    }

    @Override
    public void close() throws SQLException {
        deleteStmt.close();
        terminateStmt.close();
    }
}
