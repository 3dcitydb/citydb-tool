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

package org.citydb.database.adapter;

import org.citydb.core.version.Version;
import org.citydb.database.DatabaseException;
import org.citydb.database.Pool;
import org.citydb.database.connection.ConnectionDetails;
import org.citydb.database.metadata.DatabaseMetadata;
import org.citydb.database.schema.SchemaException;
import org.citydb.database.srs.SpatialReference;

import java.sql.*;
import java.util.Objects;
import java.util.Properties;

public abstract class DatabaseAdapter {
    private Pool pool;
    private ConnectionDetails connectionDetails;
    private SchemaAdapter schemaAdapter;
    private GeometryAdapter geometryAdapter;
    private DatabaseMetadata databaseMetadata;

    protected abstract SchemaAdapter createSchemaAdapter(DatabaseAdapter adapter);

    protected abstract GeometryAdapter createGeometryAdapter(DatabaseAdapter adapter);

    public abstract Class<?> getDriverClass();

    public abstract int getDefaultPort();

    public abstract String getConnectionString(String host, int port, String database);

    public abstract void setDefaultConnectionProperties(Properties properties);

    public final void initialize(Pool pool, ConnectionDetails connectionDetails) throws DatabaseException, SQLException {
        this.pool = Objects.requireNonNull(pool, "The database pool must not be null.");
        this.connectionDetails = Objects.requireNonNull(connectionDetails, "The connection details must not be null.");
        schemaAdapter = Objects.requireNonNull(createSchemaAdapter(this), "The schema adapter must not be null.");
        geometryAdapter = Objects.requireNonNull(createGeometryAdapter(this), "The geometry adapter must not be null.");

        if (connectionDetails.getSchema() == null) {
            connectionDetails.setSchema(schemaAdapter.getDefaultSchema());
        }

        try (Connection connection = pool.getConnection()) {
            DatabaseMetaData vendorMetadata = connection.getMetaData();
            databaseMetadata = DatabaseMetadata.of(getCityDBVersion(connection),
                    getDatabaseSrs(connection),
                    vendorMetadata.getDatabaseProductName(),
                    vendorMetadata.getDatabaseProductVersion(),
                    vendorMetadata.getDatabaseMajorVersion(),
                    vendorMetadata.getDatabaseMinorVersion());
        }

        try {
            schemaAdapter.buildSchemaMapping();
        } catch (SchemaException e) {
            throw new DatabaseException("Failed to build schema mapping.", e);
        }
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

    public final ConnectionDetails getConnectionDetails() {
        return connectionDetails;
    }

    public final DatabaseMetadata getDatabaseMetadata() {
        return databaseMetadata;
    }

    private Version getCityDBVersion(Connection connection) throws DatabaseException {
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(schemaAdapter.getCityDBVersion())) {
            if (rs.next()) {
                return Version.of(rs.getInt("major_version"),
                        rs.getInt("minor_version"),
                        rs.getInt("minor_revision"),
                        rs.getString("version"));
            }
        } catch (SQLException e) {
            throw new DatabaseException("Failed to retrieve the version of the 3DCityDB.", e);
        }

        throw new DatabaseException("Failed to retrieve the version of the 3DCityDB.");
    }

    private SpatialReference getDatabaseSrs(Connection connection) throws DatabaseException {
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(schemaAdapter.getDatabaseSrs())) {
            if (rs.next()) {
                return SpatialReference.of(rs.getInt("srid"),
                        schemaAdapter.getSpatialReferenceType(rs.getString("coord_ref_sys_kind")),
                        rs.getString("coord_ref_sys_name"),
                        rs.getString("srs_name"),
                        rs.getString("wktext"));
            }
        } catch (SQLException e) {
            throw new DatabaseException("Failed to retrieve the spatial reference system of the 3DCityDB.", e);
        }

        throw new DatabaseException("Failed to retrieve the spatial reference system of the 3DCityDB.");
    }
}
