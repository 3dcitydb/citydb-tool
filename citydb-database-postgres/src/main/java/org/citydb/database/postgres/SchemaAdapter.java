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

package org.citydb.database.postgres;

import org.citydb.core.concurrent.LazyCheckedInitializer;
import org.citydb.core.version.Version;
import org.citydb.database.adapter.DatabaseAdapter;
import org.citydb.database.metadata.DatabaseProperty;
import org.citydb.model.property.RelationType;
import org.citydb.sqlbuilder.common.SqlObject;
import org.citydb.sqlbuilder.function.Function;
import org.citydb.sqlbuilder.literal.BooleanLiteral;
import org.citydb.sqlbuilder.literal.IntegerLiteral;
import org.citydb.sqlbuilder.literal.StringLiteral;
import org.citydb.sqlbuilder.operation.Case;
import org.citydb.sqlbuilder.operation.Not;
import org.citydb.sqlbuilder.query.CommonTableExpression;
import org.citydb.sqlbuilder.query.Select;
import org.citydb.sqlbuilder.schema.Table;
import org.citydb.sqlbuilder.util.PlainSql;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;

public class SchemaAdapter extends org.citydb.database.adapter.SchemaAdapter {
    private final LazyCheckedInitializer<String, IOException> featureHierarchyQuery;
    private final LazyCheckedInitializer<String, IOException> recursiveImplicitGeometryQuery;
    private final SqlHelper sqlHelper;
    private final OperationHelper operationHelper;
    private final IndexHelper indexHelper;
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
    public String getFeatureHierarchyQuery() {
        try {
            return featureHierarchyQuery.get();
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
    public Select getRecursiveLodQuery(Set<String> lods, boolean requireAll, int searchDepth, Table table) {
        Table hierarchy = Table.of("hierarchy");
        Table property = Table.of(org.citydb.database.schema.Table.PROPERTY.getName(),
                adapter.getConnectionDetails().getSchema());

        Select featureQuery = Select.newInstance()
                .select(table.column("id").as("val_feature_id"));
        Select propertyQuery = Select.newInstance()
                .select(property.column("val_feature_id"))
                .from(property)
                .join(hierarchy).on(hierarchy.column("val_feature_id").eq(property.column("feature_id")))
                .where(property.column("val_feature_id").isNotNull(),
                        property.column("val_relation_type").eq(RelationType.CONTAINS.getDatabaseValue()));

        CommonTableExpression cte = CommonTableExpression.of(hierarchy.getName(), featureQuery.unionAll(propertyQuery));
        Select select = Select.newInstance()
                .withRecursive(cte)
                .select(IntegerLiteral.of(1))
                .from(property)
                .join(cte.asTable()).on(cte.asTable().column("val_feature_id").eq(property.column("feature_id")))
                .fetch(1);

        if (searchDepth >= 0 && searchDepth != Integer.MAX_VALUE) {
            featureQuery.select(IntegerLiteral.of(0).as("depth"));
            propertyQuery.select(Case.newInstance()
                    .when(property.column("namespace_id").eq(1).and(property.column("name").eq("boundary")))
                    .then(PlainSql.of("depth"))
                    .orElse(PlainSql.of("depth").plus(1)));
            propertyQuery.where(hierarchy.column("depth").lt(IntegerLiteral.of(searchDepth + 1)));
            select.where(cte.asTable().column("depth").lt(IntegerLiteral.of(searchDepth + 1)));
        } else {
            featureQuery.select(BooleanLiteral.FALSE.as("is_cycle"),
                    PlainSql.of("array[]::bigint[]").as("path"));
            propertyQuery.select(property.column("id").eqAny(PlainSql.of("(path)")),
                            PlainSql.of("path || {}", property.column("id")))
                    .where(Not.of(hierarchy.column("is_cycle")));
        }

        if (!lods.isEmpty()) {
            select.where(adapter.getSchemaAdapter().getOperationHelper()
                    .in(property.column("val_lod"), lods.stream().map(StringLiteral::of).toList()));
            if (requireAll && lods.size() > 1) {
                select.having(Function.of("count", property.column("val_lod")).qualifier("distinct")
                        .eq(lods.size()));
            }
        } else {
            select.where(property.column("val_lod").isNotNull());
        }

        return select;
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
    public SequenceHelper getSequenceHelper(Connection connection) throws SQLException {
        return new SequenceHelper(connection, adapter);
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
        String sql = version.compareTo(Version.of(5, 1, 0)) < 0 ?
                "select coalesce(( " +
                        "select 1 from information_schema.schemata s " +
                        "join information_schema.tables t on t.table_schema = s.schema_name " +
                        "where s.schema_name = ? and t.table_name = 'database_srs' " +
                        "limit 1), 0)" :
                "select citydb_pkg.schema_exists(?)";

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
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(Objects.requireNonNull(
                SchemaAdapter.class.getResourceAsStream("/org/citydb/database/postgres/query_feature_hierarchy.sql"))))) {
            return reader.lines()
                    .collect(Collectors.joining(" "))
                    .replace("@SCHEMA@", adapter.getConnectionDetails().getSchema())
                    .replace("@JSON@", adapter.getDatabaseMetadata().getVersion().compareTo(Version.of(5, 1, 0)) < 0 ?
                            "json" :
                            "jsonb");
        }
    }

    private String readRecursiveImplicitGeometryQuery() throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(Objects.requireNonNull(
                SchemaAdapter.class.getResourceAsStream("/org/citydb/database/postgres/query_recursive_implicit_geometry.sql"))))) {
            return reader.lines()
                    .collect(Collectors.joining(" "))
                    .replace("@SCHEMA@", adapter.getConnectionDetails().getSchema());
        }
    }
}
