/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.database.postgres;

import org.citydb.core.concurrent.LazyCheckedInitializer;
import org.citydb.core.version.Version;
import org.citydb.database.adapter.DatabaseAdapter;
import org.citydb.database.metadata.DatabaseProperty;
import org.citydb.sqlbuilder.common.SqlObject;
import org.citydb.sqlbuilder.function.Function;
import org.citydb.sqlbuilder.query.Select;
import org.citydb.sqlbuilder.schema.Table;
import org.citydb.sqlbuilder.util.PlainSql;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class SchemaAdapter extends org.citydb.database.adapter.SchemaAdapter {
    private final LazyCheckedInitializer<String, IOException> featureHierarchyQuery;
    private final LazyCheckedInitializer<String, IOException> recursiveImplicitGeometryQuery;
    private final SqlHelper sqlHelper;
    private final OperationHelper operationHelper;
    private final IndexHelper indexHelper;
    private final LodHelper lodHelper;
    private final StatisticsHelper statisticsHelper;
    private final TempTableHelper tempTableHelper;
    private final ChangelogHelper changelogHelper;

    SchemaAdapter(DatabaseAdapter adapter) {
        super(adapter);
        featureHierarchyQuery = LazyCheckedInitializer.of(this::readFeatureHierarchyQuery);
        recursiveImplicitGeometryQuery = LazyCheckedInitializer.of(this::readRecursiveImplicitGeometryQuery);
        sqlHelper = new SqlHelper(adapter);
        operationHelper = new OperationHelper(this);
        indexHelper = new IndexHelper(adapter);
        lodHelper = new LodHelper(adapter);
        statisticsHelper = new StatisticsHelper(adapter);
        tempTableHelper = new TempTableHelper(adapter);
        changelogHelper = new ChangelogHelper(adapter);
    }

    @Override
    public String getDefaultSchema() {
        return "citydb";
    }

    @Override
    public Optional<Table> getDummyTable() {
        return Optional.empty();
    }

    @Override
    public int getDefaultBatchSize() {
        return 1000;
    }

    @Override
    public int getMaximumNumberOfItemsForInOperator() {
        return 1000;
    }

    @Override
    public String getFeatureHierarchyQuery(int targetSRID) {
        try {
            int srid = adapter.getDatabaseMetadata().getSpatialReference().getSRID();
            return featureHierarchyQuery.get()
                    .replace("@VAL_IMPLICITGEOM_REFPOINT@", srid != targetSRID
                            ? "st_transform(val_implicitgeom_refpoint, " + targetSRID + ") val_implicitgeom_refpoint"
                            : "val_implicitgeom_refpoint");
        } catch (Exception e) {
            throw new IllegalStateException("Failed to create feature hierarchy query.", e);
        }
    }

    @Override
    public SqlObject getRecursiveImplicitGeometryQuery(Select featureQuery) {
        try {
            return PlainSql.of(recursiveImplicitGeometryQuery.get(), featureQuery);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to create recursive implicit geometry query.", e);
        }
    }

    @Override
    public SqlHelper getSqlHelper() {
        return sqlHelper;
    }

    @Override
    public OperationHelper getOperationHelper() {
        return operationHelper;
    }

    @Override
    public IndexHelper getIndexHelper() {
        return indexHelper;
    }

    @Override
    public LodHelper getLodHelper() {
        return lodHelper;
    }

    @Override
    public SequenceHelper getSequenceHelper(Connection connection) throws SQLException {
        return new SequenceHelper(connection, adapter);
    }

    @Override
    public DeleteHelper getDeleteHelper(Connection connection) {
        return new DeleteHelper(connection, adapter);
    }

    @Override
    public EnvelopeHelper getEnvelopeHelper(Connection connection) {
        return new EnvelopeHelper(connection, adapter);
    }

    @Override
    public StatisticsHelper getStatisticsHelper() {
        return statisticsHelper;
    }

    @Override
    public TempTableHelper getTempTableHelper() {
        return tempTableHelper;
    }

    @Override
    public ChangelogHelper getChangelogHelper() {
        return changelogHelper;
    }

    @Override
    protected Version getCityDBVersion(Connection connection) throws SQLException {
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("select major_version, minor_version, minor_revision, version " +
                     "from citydb_pkg.citydb_version()")) {
            if (rs.next()) {
                return Version.of(rs.getInt("major_version"),
                        rs.getInt("minor_version"),
                        rs.getInt("minor_revision"),
                        rs.getString("version"));
            }
        }

        return null;
    }

    @Override
    protected boolean schemaExists(String schemaName, Version version, Connection connection) throws SQLException {
        String sql = version.compareTo(Version.of(5, 1, 0)) < 0
                ? "select coalesce(( " +
                "select 1 from information_schema.schemata s " +
                "join information_schema.tables t on t.table_schema = s.schema_name " +
                "where s.schema_name = ? and t.table_name = 'database_srs' " +
                "limit 1), 0)"
                : "select citydb_pkg.schema_exists(?)";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, schemaName);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() && rs.getBoolean(1);
            }
        }
    }

    @Override
    protected List<DatabaseProperty> getDatabaseProperties(Version version, Connection connection) throws SQLException {
        List<DatabaseProperty> properties = new ArrayList<>();
        if (version.compareTo(Version.of(5, 1, 0)) < 0) {
            properties.add(getDatabaseProperty(PostgresqlAdapter.PROPERTY_POSTGIS, "PostGIS",
                    Select.newInstance().select(Function.of("postgis_lib_version")), connection));
            properties.add(getDatabaseProperty(PostgresqlAdapter.PROPERTY_POSTGIS_SFCGAL, "SFCGAL",
                    Select.newInstance().select(Function.of("postgis_sfcgal_version")), connection));
        } else {
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery("select id, name, value from citydb_pkg.db_properties()")) {
                while (rs.next()) {
                    String id = rs.getString(1);
                    String name = rs.getString(2);
                    if (id != null && name != null) {
                        properties.add(DatabaseProperty.of(id, name, rs.getString(3)));
                    }
                }
            }
        }

        return properties;
    }

    private DatabaseProperty getDatabaseProperty(String id, String name, Select value, Connection connection) {
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(value.toSql())) {
            if (rs.next()) {
                return DatabaseProperty.of(id, name, rs.getString(1));
            }
        } catch (SQLException e) {
            //
        }

        return DatabaseProperty.of(id, name);
    }

    private String readFeatureHierarchyQuery() throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(SchemaAdapter.class.getResourceAsStream(
                "/org/citydb/database/postgres/query_feature_hierarchy.sql")))) {
            return reader.lines()
                    .collect(Collectors.joining(" "))
                    .replace("@SCHEMA@", adapter.getConnectionDetails().getSchema())
                    .replace("@JSON@", adapter.getDatabaseMetadata().getVersion().compareTo(Version.of(5, 1, 0)) < 0
                            ? "json"
                            : "jsonb");
        }
    }

    private String readRecursiveImplicitGeometryQuery() throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(SchemaAdapter.class.getResourceAsStream(
                "/org/citydb/database/postgres/query_recursive_implicit_geometry.sql")))) {
            return reader.lines()
                    .collect(Collectors.joining(" "))
                    .replace("@SCHEMA@", adapter.getConnectionDetails().getSchema());
        }
    }
}
