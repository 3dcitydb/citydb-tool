/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.database.postgres;

import org.citydb.core.version.Version;
import org.citydb.database.DatabaseException;
import org.citydb.database.adapter.DatabaseAdapter;
import org.citydb.database.adapter.DatabaseType;
import org.postgresql.Driver;
import org.postgresql.PGProperty;

import java.util.Optional;
import java.util.Properties;

@DatabaseType(name = PostgresqlAdapter.DATABASE_NAME)
public class PostgresqlAdapter extends DatabaseAdapter {
    public static final String DATABASE_NAME = "PostgreSQL";
    public static final String PROPERTY_POSTGIS = "postgis";
    public static final String PROPERTY_POSTGIS_SFCGAL = "postgis_sfcgal";

    private Version postgisVersion;
    private Version sfcgalVersion;

    @Override
    public Class<?> getDriverClass() {
        return Driver.class;
    }

    @Override
    public int getDefaultPort() {
        return 5432;
    }

    @Override
    public String getConnectionString(String host, int port, String database) {
        return "jdbc:postgresql://" + host + ":" + port + "/" + database;
    }

    @Override
    public void setDefaultConnectionProperties(Properties properties) {
        PGProperty.DEFAULT_ROW_FETCH_SIZE.set(properties, 1000);
        PGProperty.REWRITE_BATCHED_INSERTS.set(properties, true);
        PGProperty.TCP_KEEP_ALIVE.set(properties, true);
    }

    @Override
    protected SchemaAdapter createSchemaAdapter(DatabaseAdapter adapter) {
        return new SchemaAdapter(adapter);
    }

    @Override
    protected GeometryAdapter createGeometryAdapter(DatabaseAdapter adapter) {
        return new GeometryAdapter(adapter);
    }

    Version getPostGISVersion() {
        return postgisVersion;
    }

    boolean hasSFCGALSupport() {
        return sfcgalVersion != null;
    }

    Optional<Version> getSCFGALVersion() {
        return Optional.ofNullable(sfcgalVersion);
    }

    @Override
    protected void postInitialize() throws DatabaseException {
        postgisVersion = getDatabaseMetadata().getProperty(PostgresqlAdapter.PROPERTY_POSTGIS)
                .flatMap(property -> Version.parse(property.getValue().orElse(null)))
                .orElseThrow(() -> new DatabaseException("Failed to retrieve PostGIS version."));
        sfcgalVersion = getDatabaseMetadata().getProperty(PostgresqlAdapter.PROPERTY_POSTGIS_SFCGAL)
                .flatMap(property -> Version.parse(property.getValue().orElse(null)))
                .orElse(null);
    }
}
