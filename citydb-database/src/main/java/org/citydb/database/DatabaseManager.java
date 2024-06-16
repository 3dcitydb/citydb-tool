/*
 * citydb-tool - Command-line tool for the 3D City Database
 * https://www.3dcitydb.org/
 *
 * Copyright 2022-2024
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

package org.citydb.database;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;
import org.apache.tomcat.jdbc.pool.DataSource;
import org.apache.tomcat.jdbc.pool.PoolProperties;
import org.citydb.database.adapter.DatabaseAdapter;
import org.citydb.database.adapter.DatabaseAdapterException;
import org.citydb.database.adapter.DatabaseAdapterManager;
import org.citydb.database.connection.ConnectionDetails;
import org.citydb.database.connection.PoolOptions;
import org.citydb.logging.LoggerManager;

import java.sql.SQLException;
import java.util.Objects;
import java.util.function.Consumer;

public class DatabaseManager {
    private final Logger logger = LoggerManager.getInstance().getLogger(DatabaseManager.class);
    private DatabaseAdapter adapter;
    private DataSource dataSource;

    private DatabaseManager() {
    }

    public static DatabaseManager newInstance() {
        return new DatabaseManager();
    }

    public void connect(ConnectionDetails connectionDetails, DatabaseAdapterManager manager) throws DatabaseException, SQLException {
        Objects.requireNonNull(connectionDetails, "The connection details must not be null.");
        ConnectionDetails connection = new ConnectionDetails()
                .setDatabaseName(connectionDetails.getDatabaseName("PostgreSQL"))
                .setHost(connectionDetails.getHost(System.getenv(DatabaseConstants.ENV_CITYDB_HOST)))
                .setPort(connectionDetails.getPort(System.getenv(DatabaseConstants.ENV_CITYDB_PORT)))
                .setDatabase(connectionDetails.getDatabase(System.getenv(DatabaseConstants.ENV_CITYDB_NAME)))
                .setSchema(connectionDetails.getSchema(System.getenv(DatabaseConstants.ENV_CITYDB_SCHEMA)))
                .setUser(connectionDetails.getUser(System.getenv(DatabaseConstants.ENV_CITYDB_USERNAME)))
                .setPassword(connectionDetails.getPassword(System.getenv(DatabaseConstants.ENV_CITYDB_PASSWORD)))
                .setPoolOptions(connectionDetails.getPoolOptions().orElseGet(PoolOptions::new));

        adapter = manager.getAdapterForDatabase(connection.getDatabaseName());
        if (adapter == null) {
            throw new DatabaseException("No database adapter available for the database '" +
                    connection.getDatabaseName() + "'.");
        } else if (connection.getHost() == null) {
            throw new DatabaseException("Missing host name for database connection.");
        } else if (connection.getDatabase() == null) {
            throw new DatabaseException("Missing database name for database connection.");
        } else if (connection.getUser() == null) {
            throw new DatabaseException("Missing username for database connection.");
        } else if (connection.getPort() == null) {
            connection.setPort(adapter.getDefaultPort());
        }

        PoolProperties properties = new PoolProperties();
        properties.setDriverClassName(adapter.getDriverClass().getName());
        properties.setUsername(connection.getUser());
        properties.setPassword(connection.getPassword());
        properties.setUrl(adapter.getConnectionString(connection.getHost(),
                connection.getPort(),
                connection.getDatabase()));

        properties.setInitialSize(0);
        properties.setDefaultAutoCommit(true);

        dataSource = new DataSource(properties);
        dataSource.setLoginTimeout(connection.getPoolOptions()
                .map(PoolOptions::getLoginTimeout)
                .orElse(PoolOptions.DEFAULT_LOGIN_TIMEOUT));
        dataSource.createPool();

        adapter.initialize(Pool.newInstance(this), connection);
    }

    public void connect(ConnectionDetails connectionDetails) throws DatabaseException, SQLException {
        try {
            connect(connectionDetails, DatabaseAdapterManager.newInstance().load());
        } catch (DatabaseAdapterException e) {
            throw new DatabaseException("Failed to load database adapters.", e);
        }
    }

    public boolean isConnected() {
        return dataSource != null
                && dataSource.getPool() != null
                && !dataSource.getPool().isClosed();
    }

    public void disconnect() {
        dataSource.close(true);
        dataSource = null;
    }

    public DatabaseAdapter getAdapter() {
        return adapter;
    }

    DataSource getDataSource() {
        return dataSource;
    }

    public void logDatabaseMetadata(Level level) {
        printDatabaseMetadata(s -> logger.log(level, s));
    }

    public void printDatabaseMetadata(Consumer<String> consumer) {
        if (isConnected()) {
            consumer.accept("3D City Database: " + adapter.getDatabaseMetadata().getVersion());
            consumer.accept("DBMS: " + adapter.getDatabaseMetadata().getVendorProductName() + " " +
                    adapter.getDatabaseMetadata().getVendorProductVersion());
            consumer.accept("Connection: " + adapter.getConnectionDetails().toConnectString());
            consumer.accept("Schema: " + adapter.getConnectionDetails().getSchema());
            consumer.accept("SRID: " + adapter.getDatabaseMetadata().getSpatialReference().getSRID());
            consumer.accept("SRS name: " + adapter.getDatabaseMetadata().getSpatialReference().getName());
            consumer.accept("SRS URI: " + adapter.getDatabaseMetadata().getSpatialReference().getIdentifier());
        }
    }
}
