/*
 * citydb-tool - Command-line tool for the 3D City Database
 * https://www.3dcitydb.org/
 *
 * Copyright © 2025, Oracle and/or its affiliates.
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

package org.citydb.database.oracle;

import oracle.spatial.util.Util;
import org.citydb.core.concurrent.LazyCheckedInitializer;
import org.citydb.core.version.Version;
import org.citydb.database.adapter.DatabaseAdapter;
import org.citydb.database.metadata.DatabaseProperty;
import org.citydb.database.schema.Index;
import org.citydb.database.schema.Sequence;
import org.citydb.database.srs.SpatialReferenceType;
import org.citydb.database.util.ChangelogHelper;
import org.citydb.model.property.RelationType;
import org.citydb.sqlbuilder.common.SqlObject;
import org.citydb.sqlbuilder.function.Function;
import org.citydb.sqlbuilder.literal.IntegerLiteral;
import org.citydb.sqlbuilder.literal.StringLiteral;
import org.citydb.sqlbuilder.operation.Case;
import org.citydb.sqlbuilder.query.CommonTableExpression;
import org.citydb.sqlbuilder.query.Select;
import org.citydb.sqlbuilder.schema.Table;
import org.citydb.sqlbuilder.util.PlainText;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class SchemaAdapter extends org.citydb.database.adapter.SchemaAdapter {
    private final Logger logger = LoggerFactory.getLogger(SchemaAdapter.class);
    protected static final String PROPERTY_ORACLE_SPATIAL = "oracle_spatial";
    protected static final int MAX_SQL_NAME_LENGTH = 128;

    private final LazyCheckedInitializer<String, IOException> featureHierarchyQuery;
    private final LazyCheckedInitializer<String, IOException> recursiveImplicitGeometryQuery;
    private final OperationHelper operationHelper;
    private final StatisticsHelper statisticsHelper;
    private final TempTableHelper tempTableHelper;

    SchemaAdapter(DatabaseAdapter adapter) {
        super(adapter);
        featureHierarchyQuery = LazyCheckedInitializer.of(this::readFeatureHierarchyQuery);
        recursiveImplicitGeometryQuery = LazyCheckedInitializer.of(this::readRecursiveImplicitGeometryQuery);
        operationHelper = new OperationHelper(this);
        statisticsHelper = new StatisticsHelper(adapter);
        tempTableHelper = new TempTableHelper(adapter);
    }

    @Override
    public int getOtherSqlType(){
        return oracle.jdbc.OracleTypes.JSON;
    }

    @Override
    public String getDefaultSchema() {
      return "citydb";
    }

    @Override
    public Optional<Table> getDummyTable() {
      return Optional.empty();
    }

    public String getNextSequenceValues(Sequence sequence) {
        return "select " +
                enquoteSqlName(adapter.getConnectionDetails().getSchema()) + "." +
                enquoteSqlName(sequence.toString()) + ".nextval " +
                " from dual connect by level <= ?";
    }

    @Override
    public int getMaximumBatchSize() {
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
            return PlainText.of(recursiveImplicitGeometryQuery.get(), featureQuery);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to create recursive implicit geometry query.", e);
        }
    }

    @Override
    public Select getRecursiveLodQuery(Set<String> lods, boolean requireAll, int searchDepth, Table table) {
        Table hierarchy = Table.of("hierarchy");
        Table property = Table.of(
                enquoteSqlName(org.citydb.database.schema.Table.PROPERTY.getName()),
                enquoteSqlName(adapter.getConnectionDetails().getSchema()));

        Select featureQuery = Select.newInstance()
                .select(table.column("id").as("feature_id"),
                        table.column("id").as("val_feature_id"),
                        IntegerLiteral.of(0).as("val_relation_type"));
        Select propertyQuery = Select.newInstance()
                .select(property.column("feature_id"),
                        property.column("val_feature_id"),
                        property.column("val_relation_type"))
                .from(property)
                .join(hierarchy).on(hierarchy.column("val_feature_id").eq(property.column("feature_id")))
                .where(property.column("val_feature_id").isNotNull(),
                        property.column("val_relation_type").eq(RelationType.CONTAINS.getDatabaseValue()));
        Select hierarchyQuery = Select.newInstance()
                .withRecursive(CommonTableExpression.of(hierarchy.getName(), featureQuery.unionAll(propertyQuery)))
                .select(hierarchy.column("val_feature_id"))
                .from(hierarchy);

        if (searchDepth >= 0 && searchDepth != Integer.MAX_VALUE) {
            featureQuery.select(IntegerLiteral.of(0).as("depth"));
            propertyQuery.select(Case.newInstance()
                            .when(property.column("namespace_id").eq(1).and(property.column("name").eq("boundary")))
                            .then(PlainText.of("depth"))
                            .orElse(PlainText.of("depth").plus(1)))
                    .where(PlainText.of("depth").lt(IntegerLiteral.of(searchDepth + 1)));
            hierarchyQuery.where(PlainText.of("depth").lt(IntegerLiteral.of(searchDepth + 1)));
        }

        hierarchy = Table.of(hierarchyQuery);
        property = Table.of(
                enquoteSqlName(org.citydb.database.schema.Table.PROPERTY.getName()),
                enquoteSqlName(adapter.getConnectionDetails().getSchema()));

        Select select = Select.newInstance()
                .select(IntegerLiteral.of(1))
                .from(property)
                .join(hierarchy).on(hierarchy.column("val_feature_id").eq(property.column("feature_id")))
                .fetch(1);

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
        logger.debug("getResursiveLodQuery: " + select.toString());
        return select;
    }

    @Override
    public String getCreateIndex(Index index, boolean ignoreNulls) {
        String stmt = "create index if not exists " + enquoteSqlName(index.getName()) +
                " on " + enquoteSqlName(adapter.getConnectionDetails().getSchema()) + "." +
                enquoteSqlName(index.getTable()) +
                "(" + String.join(", ", enquoteSqlNames(index.getColumns())) + ")" +
                (index.getType() == Index.Type.SPATIAL ? " INDEXTYPE IS MDSYS.SPATIAL_INDEX_V2 " : " ") ;
        logger.debug("getCreateIndex: " + stmt);
        //Oracle does not index null values by default.
        //Need to use cumbersome workaround in order to NOT ignore null.
        //Will consider one of the workarounds if there is a clear need on indexing null values.
//        if (ignoreNulls) {
//            stmt += " where " + index.getColumns().stream()
//                    .map(column -> column + " is not null")
//                    .collect(Collectors.joining(" and "));
//        }
        return stmt;
    }

    @Override
    public String getDropIndex(Index index) {
        String sql = "drop index if exists " +
                enquoteSqlName(adapter.getConnectionDetails().getSchema()) + "." +
                enquoteSqlName(index.getName());
        logger.debug("getDropIndex: "+sql);
        return sql;
    }

    @Override
    public String getIndexExists(Index index) {
        String sql = "select 1 " +
                " from all_indexes " +
                " where owner = '" + enquoteSqlName(adapter.getConnectionDetails().getSchema()) + "' " +
                " and index_name = '" + enquoteSqlName(index.getName()) +
                "' and rownum = 1";
        logger.debug("getIndexExists: "+sql);
        return sql;
    }

    @Override
    public OperationHelper getOperationHelper() {
        return operationHelper;
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
        return null;
    }

    @Override
    protected String getCityDBVersion() {
        String sql = "select major_version, minor_version, minor_revision, version from citydb_util.citydb_version()";
        logger.debug("getCityDBVersion: " + sql);
        return sql;
    }

    @Override
    protected String getSchemaExists(String schemaName, Version version) {
        String sql = "select 1 " +
              " from all_users " +
              " where username = '" + checkSimpleSqlName(adapter.getConnectionDetails().getSchema().toUpperCase()) + "' " +
              " and rownum = 1";
        logger.debug("getSchemaExists: "+sql);
        return sql;
    }

    @Override
    protected String getDatabaseSrs() {
        String sql = "select srid, srs_name, coord_ref_sys_name, coord_ref_sys_kind, wktext " +
                "from citydb_util.db_metadata('" + enquoteSqlName(adapter.getConnectionDetails().getSchema()) + "')";
        logger.debug("getDatabaseSrs: " + sql);
        return sql;
    }

    @Override
    protected String getSpatialReference(int srid) {
        assert(srid>=0);
        String sql = "select coord_ref_sys_name, coord_ref_sys_kind, wktext " +
                "from citydb_srs.get_coord_ref_sys_info(" + srid + ")";
        logger.debug("getSpatialReference: "+sql);
        return sql;
    }

    @Override
    protected SpatialReferenceType getSpatialReferenceType(String type) {
        return switch (type.toUpperCase(Locale.ROOT)) {
            case "PROJCRS", "PROJECTEDCRS", "PROJCS" -> SpatialReferenceType.PROJECTED_CRS;
            case "GEOGCRS", "GEOGRAPHICCRS", "GEOGCS" -> SpatialReferenceType.GEOGRAPHIC_CRS;
            case "GEODCRS", "GEODETICCRS" -> SpatialReferenceType.GEODETIC_CRS;
            case "GEOCCS" -> SpatialReferenceType.GEOCENTRIC_CRS;
            case "COMPOUNDCRS", "COMPDCS", "COMPD_CS" -> SpatialReferenceType.COMPOUND_CRS;
            case "ENGCRS", "ENGINEERINGCRS", "LOCAL_CS" -> SpatialReferenceType.ENGINEERING_CRS;
            default -> SpatialReferenceType.UNKNOWN_CRS;
        };
    }

    @Override
    protected String getChangelogEnabled(String schemaName) {
        return "select exists ( " +
                "select 1 from all_triggers s " +
                "where s.owner = '" + enquoteSqlName(schemaName) + "' " +
                "and table_name = '" + enquoteSqlName(org.citydb.database.schema.Table.FEATURE.getName()) + "' " +
                "and trigger_name = 'feature_changelog_trigger'" +
                ")";
    }

    @Override
    protected List<DatabaseProperty> getDatabaseProperties(Version version, Connection connection) throws SQLException {
        List<DatabaseProperty> properties = new ArrayList<>();
        properties.add(getDatabaseProperty(PROPERTY_ORACLE_SPATIAL, "Oracle Spatial",
                Select.newInstance().select(StringLiteral.of("supported")), connection));
        return properties;
    }

    private DatabaseProperty getDatabaseProperty(String id, String name, Select value, Connection connection) {
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(value.toSql())) {
            if (rs.next()) {
                return DatabaseProperty.of(id, name, rs.getString(1));
            }
        } catch (SQLException e) {
            logger.error("Failed to get database property: "+id+", "+name+": ", e);
        }
        return DatabaseProperty.of(id, name);
    }

    private String readFeatureHierarchyQuery() throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(Objects.requireNonNull(
                SchemaAdapter.class.getResourceAsStream("/org/citydb/database/oracle/query_feature_hierarchy.sql"))))) {
            return reader.lines()
                    .collect(Collectors.joining(" "))
                    .replace("@SCHEMA@", enquoteSqlName(adapter.getConnectionDetails().getSchema()));
        }
    }

    private String readRecursiveImplicitGeometryQuery() throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(Objects.requireNonNull(
                SchemaAdapter.class.getResourceAsStream("/org/citydb/database/oracle/query_recursive_implicit_geometry.sql"))))) {
            return reader.lines()
                    .collect(Collectors.joining(" "))
                    .replace("@SCHEMA@", enquoteSqlName(adapter.getConnectionDetails().getSchema()));
        }
    }

    static String checkSqlType(String type){
        try {
            return Util.checkSQLName(type, MAX_SQL_NAME_LENGTH, Util.CheckType.SQLType);
        }
        catch (SQLException e) {
            throw new RuntimeException("Invalid sql type:" + type, e);
        }
    }

    static String checkSimpleSqlName(String name){
        try {
            return Util.checkSQLName(name, MAX_SQL_NAME_LENGTH, Util.CheckType.sqlName);
        }
        catch (SQLException e) {
            throw new RuntimeException("Invalid sql name:" + name, e);
        }
    }

    static List<String> checkSimpleSqlNames(List<String> names){
        List<String> checkedNames = new ArrayList<>();
        for(String name : names){
            checkedNames.add(checkSimpleSqlName(name));
        }
        return checkedNames;
    }

    static String enquoteSqlName(String name){
        try {
            return Util.enquoteNameSQLName(name);
        }
        catch (SQLException e) {
            throw new RuntimeException("Invalid sql name:" + name, e);
        }
    }

    static List<String> enquoteSqlNames(List<String> names){
        List<String> quotedNames = new ArrayList<>();
        for(String name : names){
            quotedNames.add(enquoteSqlName(name));
        }
        return quotedNames;
    }

}