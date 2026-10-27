/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.cli.util;

import org.citydb.cli.ExecutionException;
import org.citydb.database.adapter.DatabaseAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;

public class RollbackConnection implements AutoCloseable {
    private final Logger logger = LoggerFactory.getLogger(RollbackConnection.class);
    private final Connection connection;

    private RollbackConnection(Connection connection) {
        this.connection = connection;
    }

    public static RollbackConnection of(DatabaseAdapter adapter) throws ExecutionException {
        try {
            return new RollbackConnection(adapter.getPool().getConnection(false));
        } catch (SQLException e) {
            throw new ExecutionException("Failed to get a database connection.", e);
        }
    }

    public Connection getConnection() {
        return connection;
    }

    @Override
    public void close() {
        try (Connection connection = this.connection) {
            connection.rollback();
        } catch (SQLException e) {
            logger.warn("Failed to release the database connection.", e);
        }
    }
}
