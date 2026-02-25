/*
 * citydb-tool - Command-line tool for the 3D City Database
 * https://www.3dcitydb.org/
 *
 * Copyright 2022-2025
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

package org.citydb.database.adapter;

import org.citydb.core.version.Version;
import org.citydb.database.DatabaseConstants;
import org.citydb.database.DatabaseException;
import org.citydb.database.Pool;
import org.citydb.database.connection.ConnectionDetails;
import org.citydb.database.metadata.DatabaseMetadata;
import org.citydb.database.schema.SchemaException;
import org.citydb.database.srs.SpatialReference;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;
import java.util.Properties;

public abstract class DatabaseAdapter {
    private Pool pool;
    private ConnectionDetails connectionDetails;
    private SchemaAdapter schemaAdapter;
    private GeometryAdapter geometryAdapter;
    private DatabaseMetadata databaseMetadata;

    public abstract Class<?> getDriverClass();

    public abstract int getDefaultPort();

    public abstract String getConnectionString(String host, int port, String database);

    public abstract void setDefaultConnectionProperties(Properties properties);

    protected abstract SchemaAdapter createSchemaAdapter(DatabaseAdapter adapter);

    protected abstract GeometryAdapter createGeometryAdapter(DatabaseAdapter adapter);

    public final void initialize(Pool pool, ConnectionDetails connectionDetails) throws DatabaseException, SQLException {
        this.pool = Objects.requireNonNull(pool, "The database pool must not be null.");
        this.connectionDetails = ConnectionDetails.of(Objects.requireNonNull(connectionDetails,
                "The connection details must not be null."));

        schemaAdapter = Objects.requireNonNull(createSchemaAdapter(this), "The schema adapter must not be null.");
        geometryAdapter = Objects.requireNonNull(createGeometryAdapter(this), "The geometry adapter must not be null.");

        String defaultSchema = schemaAdapter.getDefaultSchema();
        if (this.connectionDetails.getSchema() == null) {
            this.connectionDetails.setSchema(defaultSchema);
            connectionDetails.setSchema(defaultSchema);
        }

        try (Connection connection = pool.getConnection()) {
            Version version = getCityDBVersion(connection);

            String schema = this.connectionDetails.getSchema();
            if (!schema.equals(defaultSchema) && !schemaExists(schema, version, connection)) {
                throw new DatabaseException("The requested schema '" + schema + "' is not a 3DCityDB schema.");
            }

            databaseMetadata = DatabaseMetadata.of(version,
                    getDatabaseSrs(schema, connection),
                    isChangelogEnabled(schema, connection),
                    connection.getMetaData(),
                    schemaAdapter::getVendorProductString,
                    schemaAdapter.getDatabaseProperties(version, connection));
        }

        try {
            schemaAdapter.buildSchemaMapping();
        } catch (SchemaException e) {
            throw new DatabaseException("Failed to build schema mapping.", e);
        }

        postInitialize();
    }

    public SchemaAdapter getSchemaAdapter() {
        return schemaAdapter;
    }

    public GeometryAdapter getGeometryAdapter() {
        return geometryAdapter;
    }

    public final Pool getPool() {
        return pool;
    }

    public final String getDatabaseName() {
        DatabaseType databaseType = getClass().getAnnotation(DatabaseType.class);
        return databaseType != null ? databaseType.name() : null;
    }

    public final ConnectionDetails getConnectionDetails() {
        return connectionDetails;
    }

    public final DatabaseMetadata getDatabaseMetadata() {
        return databaseMetadata;
    }

    protected void postInitialize() throws DatabaseException {
    }

    private Version getCityDBVersion(Connection connection) throws DatabaseException {
        try {
            Version version = schemaAdapter.getCityDBVersion(connection);
            if (version == null) {
                throw new DatabaseException("Failed to retrieve the version of the 3DCityDB.");
            }

            if (!DatabaseConstants.VERSION_SUPPORT.isSupported(version)) {
                throw new DatabaseVersionException("The " + DatabaseConstants.CITYDB_SHORT_NAME +
                        " version " + version + " is not supported. " +
                        "Supported versions are " + DatabaseConstants.VERSION_SUPPORT + ".");
            }

            return version;
        } catch (SQLException e) {
            throw new DatabaseException("Failed to retrieve the version of the 3DCityDB.", e);
        }
    }

    private boolean schemaExists(String schemaName, Version version, Connection connection) throws DatabaseException {
        try {
            return schemaAdapter.schemaExists(schemaName, version, connection);
        } catch (SQLException e) {
            throw new DatabaseException("Failed to verify whether '" + schemaName + "' is a 3DCityDB schema.", e);
        }
    }

    private SpatialReference getDatabaseSrs(String schemaName, Connection connection) throws DatabaseException {
        try {
            return geometryAdapter.getSrsHelper().getDatabaseSrs(schemaName, connection).orElseThrow(() ->
                    new DatabaseException("Failed to retrieve the spatial reference system of the 3DCityDB."));
        } catch (SQLException e) {
            throw new DatabaseException("Failed to retrieve the spatial reference system of the 3DCityDB.", e);
        }
    }

    private boolean isChangelogEnabled(String schemaName, Connection connection) throws DatabaseException {
        try {
            return schemaAdapter.getChangelogHelper().isChangelogEnabled(schemaName, connection);
        } catch (SQLException e) {
            throw new DatabaseException("Failed to verify whether the changelog is enabled for the 3DCityDB.", e);
        }
    }
}
