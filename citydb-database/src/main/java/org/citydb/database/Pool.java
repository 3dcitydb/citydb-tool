/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.database;

import java.sql.Connection;
import java.sql.SQLException;

public class Pool {
    private final DatabaseManager manager;

    private Pool(DatabaseManager manager) {
        this.manager = manager;
    }

    static Pool newInstance(DatabaseManager manager) {
        return new Pool(manager);
    }

    public Connection getConnection(boolean autoCommit) throws SQLException {
        if (manager.isConnected()) {
            Connection connection = manager.getDataSource().getConnection();
            connection.setAutoCommit(autoCommit);
            return connection;
        } else {
            throw new SQLException("No database connection established.");
        }
    }

    public Connection getConnection() throws SQLException {
        return getConnection(false);
    }
}
