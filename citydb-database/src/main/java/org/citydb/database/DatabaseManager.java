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

import org.apache.tomcat.jdbc.pool.DataSource;
import org.apache.tomcat.jdbc.pool.PoolProperties;
import org.citydb.core.version.Version;
import org.citydb.database.adapter.DatabaseAdapter;
import org.citydb.database.adapter.DatabaseAdapterException;
import org.citydb.database.adapter.DatabaseAdapterManager;
import org.citydb.database.connection.ConnectionDetails;
import org.citydb.database.connection.PoolOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.Objects;
import java.util.Properties;
import java.util.function.Consumer;

public class DatabaseManager {
    private final Logger logger = LoggerFactory.getLogger(DatabaseManager.class);
    private DatabaseAdapter adapter;
    private DataSource dataSource;

    private DatabaseManager() {
    }

    public static DatabaseManager newInstance() {
        return new DatabaseManager();
    }

    public void connect(ConnectionDetails connectionDetails, DatabaseAdapterManager manager) throws DatabaseException, SQLException {
        Objects.requireNonNull(connectionDetails, "The connection details must not be null.");
        connectionDetails.fillAbsentValuesFromEnv()
                .setDatabaseName(connectionDetails.getDatabaseNameOrElse("PostgreSQL"));

        adapter = manager.getAdapterForDatabase(connectionDetails.getDatabaseName());
        if (adapter == null) {
            throw new DatabaseException("No database adapter available for the database '" +
                    connectionDetails.getDatabaseName() + "'.");
        } else if (connectionDetails.getHost() == null) {
            throw new DatabaseException("Missing host name for database connection.");
        } else if (connectionDetails.getDatabase() == null) {
            throw new DatabaseException("Missing database name for database connection.");
        } else if (connectionDetails.getUser() == null) {
            throw new DatabaseException("Missing username for database connection.");
        } else if (connectionDetails.getPort() == null) {
            connectionDetails.setPort(adapter.getDefaultPort());
        }

        PoolProperties poolProperties = new PoolProperties();
        poolProperties.setDriverClassName(adapter.getDriverClass().getName());
        poolProperties.setUsername(connectionDetails.getUser());
        poolProperties.setPassword(connectionDetails.getPassword());
        poolProperties.setUrl(adapter.getConnectionString(connectionDetails.getHost(),
                connectionDetails.getPort(),
                connectionDetails.getDatabase()));

        poolProperties.setInitialSize(0);
        poolProperties.setDefaultAutoCommit(false);
        poolProperties.setRollbackOnReturn(true);

        Properties dbProperties = new Properties();
        adapter.setDefaultConnectionProperties(dbProperties);
        connectionDetails.getProperties().forEach((k, v) -> dbProperties.setProperty(k, String.valueOf(v)));
        poolProperties.setDbProperties(dbProperties);

        dataSource = new DataSource(poolProperties);
        dataSource.setLoginTimeout(connectionDetails.getPoolOptions()
                .map(PoolOptions::getLoginTimeout)
                .orElse(PoolOptions.DEFAULT_LOGIN_TIMEOUT));

        try {
            dataSource.createPool();
            adapter.initialize(Pool.newInstance(this), connectionDetails);
        } catch (DatabaseException | SQLException e) {
            disconnect();
            throw e;
        }

        Version version = adapter.getDatabaseMetadata().getVersion();
        if (DatabaseConstants.VERSION_SUPPORT.getPolicies().stream()
                .anyMatch(policy -> policy.getUpperBound().compareTo(version) > 0)) {
            logger.warn("The {} version {} is out of date. Consider upgrading.",
                    DatabaseConstants.CITYDB_SHORT_NAME, version);
        }
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
        if (dataSource != null) {
            dataSource.close(true);
            dataSource = null;
            adapter = null;
        }
    }

    public DatabaseAdapter getAdapter() {
        return adapter;
    }

    DataSource getDataSource() {
        return dataSource;
    }

    public void reportDatabaseInfo(Consumer<String> consumer) {
        if (isConnected()) {
            consumer.accept("3D City Database: " + adapter.getDatabaseMetadata().getVersion());
            consumer.accept("DBMS: " + adapter.getDatabaseMetadata().getVendorProductString());
            consumer.accept("Connection: " + adapter.getConnectionDetails().toConnectString());
            consumer.accept("Schema: " + adapter.getConnectionDetails().getSchema());
            consumer.accept("SRID: " + adapter.getDatabaseMetadata().getSpatialReference().getSRID());
            consumer.accept("SRS name: " + adapter.getDatabaseMetadata().getSpatialReference().getName());
            consumer.accept("SRS URI: " + adapter.getDatabaseMetadata().getSpatialReference().getIdentifier());
            consumer.accept("Changelog: " + (adapter.getDatabaseMetadata().isChangelogEnabled() ?
                    "enabled" : "disabled"));
        }
    }
}
