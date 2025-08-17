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

package org.citydb.database.util;

import org.citydb.core.tuple.Pair;
import org.citydb.database.DatabaseException;
import org.citydb.database.adapter.DatabaseAdapter;
import org.citydb.database.geometry.GeometryException;
import org.citydb.database.metadata.DatabaseSize;
import org.citydb.database.schema.DataType;
import org.citydb.database.schema.FeatureType;
import org.citydb.database.schema.SchemaMapping;
import org.citydb.model.common.Name;
import org.citydb.model.common.Namespaces;
import org.citydb.model.geometry.Envelope;
import org.citydb.model.geometry.GeometryType;
import org.citydb.sqlbuilder.function.Function;
import org.citydb.sqlbuilder.join.Join;
import org.citydb.sqlbuilder.join.Joins;
import org.citydb.sqlbuilder.literal.IntegerLiteral;
import org.citydb.sqlbuilder.operation.Case;
import org.citydb.sqlbuilder.operation.Exists;
import org.citydb.sqlbuilder.operation.Operators;
import org.citydb.sqlbuilder.query.Select;
import org.citydb.sqlbuilder.schema.Column;
import org.citydb.sqlbuilder.schema.Table;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

public abstract class StatisticsHelper {
    public static final String NULL_THEME = "<none>";
    protected final DatabaseAdapter adapter;

    protected StatisticsHelper(DatabaseAdapter adapter) {
        this.adapter = adapter;
    }

    public enum FeatureScope {
        ALL,
        ACTIVE,
        TERMINATED
    }

    public abstract DatabaseSize getDatabaseSize(Connection connection) throws DatabaseException, SQLException;

    protected abstract Column getGeometryType(Table geometryData);

    public record FeatureInfo(long count, Envelope extent) {
    }

    public record SurfaceDataInfo(boolean hasMaterials, boolean hasTextures, boolean hasGeoreferencedTextures) {
    }

    public Map<FeatureType, Long> getFeatureCount(FeatureScope scope) throws SQLException {
        try (Connection connection = adapter.getPool().getConnection(true)) {
            return getFeatureCount(scope, connection);
        }
    }

    public Map<FeatureType, Long> getFeatureCount(FeatureScope scope, Connection connection) throws SQLException {
        Table feature = Table.of(org.citydb.database.schema.Table.FEATURE.getName(), getSchema());
        Column objectClassId = feature.column("objectclass_id");
        Column terminationDate = feature.column("termination_date");

        Select select = Select.newInstance()
                .select(objectClassId, Function.of("count", feature.column("id")))
                .from(feature)
                .groupBy(objectClassId);

        switch (scope) {
            case ACTIVE -> select.where(terminationDate.isNull());
            case TERMINATED -> select.where(terminationDate.isNotNull());
        }

        Map<FeatureType, Long> featureCount = new IdentityHashMap<>();
        try (PreparedStatement stmt = connection.prepareStatement(select.toSql());
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                FeatureType featureType = getSchemaMapping().getFeatureType(rs.getInt(1));
                featureCount.put(featureType, rs.getLong(2));
            }
        }

        return featureCount;
    }

    public Map<FeatureType, FeatureInfo> getFeatureCountAndExtent(FeatureScope scope) throws DatabaseException, SQLException {
        try (Connection connection = adapter.getPool().getConnection(true)) {
            return getFeatureCountAndExtent(scope, connection);
        }
    }

    public Map<FeatureType, FeatureInfo> getFeatureCountAndExtent(FeatureScope scope, Connection connection) throws DatabaseException, SQLException {
        Table feature = Table.of(org.citydb.database.schema.Table.FEATURE.getName(), getSchema());
        Column objectClassId = feature.column("objectclass_id");
        Column terminationDate = feature.column("termination_date");

        Select select = Select.newInstance()
                .select(objectClassId, Function.of("count", feature.column("id")),
                        adapter.getGeometryAdapter().getSpatialOperationHelper().extent(feature.column("envelope")))
                .from(feature)
                .groupBy(objectClassId);

        switch (scope) {
            case ACTIVE -> select.where(terminationDate.isNull());
            case TERMINATED -> select.where(terminationDate.isNotNull());
        }

        Map<FeatureType, FeatureInfo> featureCount = new IdentityHashMap<>();
        try (PreparedStatement stmt = connection.prepareStatement(select.toSql());
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                FeatureType featureType = getSchemaMapping().getFeatureType(rs.getInt(1));
                Envelope envelope = adapter.getGeometryAdapter().getEnvelope(rs.getObject(3));
                featureCount.put(featureType, new FeatureInfo(rs.getLong(2), envelope != null ?
                        envelope.setSRID(adapter.getDatabaseMetadata().getSpatialReference().getSRID()) :
                        Envelope.empty()));
            }
        } catch (GeometryException e) {
            throw new DatabaseException("Failed to query and aggregate feature extents.", e);
        }

        return featureCount;
    }

    public Map<String, Long> getFeatureCountByLod(FeatureScope scope) throws SQLException {
        try (Connection connection = adapter.getPool().getConnection(true)) {
            return getFeatureCountByLod(scope, connection);
        }
    }

    public Map<String, Long> getFeatureCountByLod(FeatureScope scope, Connection connection) throws SQLException {
        return getCountByLod("feature_id", scope, connection);
    }

    public Map<GeometryType, Long> getGeometryCount(FeatureScope scope) throws SQLException {
        try (Connection connection = adapter.getPool().getConnection(true)) {
            return getGeometryCount(scope, connection);
        }
    }

    public Map<GeometryType, Long> getGeometryCount(FeatureScope scope, Connection connection) throws SQLException {
        Table geometryData = Table.of(org.citydb.database.schema.Table.GEOMETRY_DATA.getName(), getSchema());
        Column type = getGeometryType(geometryData);

        Select select = Select.newInstance()
                .select(type, Function.of("count", geometryData.column("id")))
                .from(geometryData)
                .groupBy(type);

        if (scope != FeatureScope.ALL) {
            joinFeatures(scope, select, geometryData.column("feature_id"), Joins.LEFT_JOIN);
        }

        Map<GeometryType, Long> geometryCount = new EnumMap<>(GeometryType.class);
        try (PreparedStatement stmt = connection.prepareStatement(select.toSql());
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                GeometryType geometryType = GeometryType.fromDatabaseValue(rs.getInt(1));
                geometryCount.put(geometryType, rs.getLong(2));
            }
        }

        return geometryCount;
    }

    public Map<String, Long> getGeometryCountByLod(FeatureScope scope) throws SQLException {
        try (Connection connection = adapter.getPool().getConnection(true)) {
            return getGeometryCountByLod(scope, connection);
        }
    }

    public Map<String, Long> getGeometryCountByLod(FeatureScope scope, Connection connection) throws SQLException {
        return getCountByLod("val_geometry_id", scope, connection);
    }

    public Pair<FeatureType, Long> getImplicitGeometryCount() throws SQLException {
        try (Connection connection = adapter.getPool().getConnection(true)) {
            return getImplicitGeometryCount(connection);
        }
    }

    public Pair<FeatureType, Long> getImplicitGeometryCount(Connection connection) throws SQLException {
        Table implicitGeometry = Table.of(org.citydb.database.schema.Table.IMPLICIT_GEOMETRY.getName(), getSchema());

        Select select = Select.newInstance()
                .select(Function.of("count", implicitGeometry.column("id")))
                .from(implicitGeometry);

        try (PreparedStatement stmt = connection.prepareStatement(select.toSql());
             ResultSet rs = stmt.executeQuery()) {
            FeatureType featureType = getSchemaMapping().getFeatureType(Name.of("ImplicitGeometry", Namespaces.CORE));
            return Pair.of(featureType, rs.next() ? rs.getLong(1) : 0);
        }
    }

    public Set<String> getLods(FeatureScope scope) throws SQLException {
        try (Connection connection = adapter.getPool().getConnection(true)) {
            return getLods(scope, connection);
        }
    }

    public Set<String> getLods(FeatureScope scope, Connection connection) throws SQLException {
        Table property = Table.of(org.citydb.database.schema.Table.PROPERTY.getName(), getSchema());
        Column lod = property.column("val_lod");

        Select select = Select.newInstance()
                .distinct(true)
                .select(lod)
                .from(property)
                .where(lod.isNotNull());

        if (scope != FeatureScope.ALL) {
            joinFeatures(scope, select, property.column("feature_id"), Joins.INNER_JOIN);
        }

        Set<String> lods = new HashSet<>();
        try (PreparedStatement stmt = connection.prepareStatement(select.toSql());
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                lods.add(rs.getString(1));
            }
        }

        return lods;
    }

    public Map<String, Long> getAppearanceCountByTheme(FeatureScope scope) throws SQLException {
        try (Connection connection = adapter.getPool().getConnection(true)) {
            return getAppearanceCountByTheme(scope, connection);
        }
    }

    public Map<String, Long> getAppearanceCountByTheme(FeatureScope scope, Connection connection) throws SQLException {
        Table appearance = Table.of(org.citydb.database.schema.Table.APPEARANCE.getName(), getSchema());
        Column theme = appearance.column("theme");

        Select select = Select.newInstance()
                .select(theme, Function.of("count", appearance.column("id")))
                .from(appearance)
                .groupBy(theme);

        if (scope != FeatureScope.ALL) {
            joinFeatures(scope, select, appearance.column("feature_id"), Joins.LEFT_JOIN);
        }

        Map<String, Long> appearanceCount = new HashMap<>();
        try (PreparedStatement stmt = connection.prepareStatement(select.toSql());
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                String name = rs.getString(1);
                appearanceCount.put(name != null ? name : NULL_THEME, rs.getLong(2));
            }
        }

        return appearanceCount;
    }

    public Set<String> getThemes(FeatureScope scope) throws SQLException {
        try (Connection connection = adapter.getPool().getConnection(true)) {
            return getThemes(scope, connection);
        }
    }

    public Set<String> getThemes(FeatureScope scope, Connection connection) throws SQLException {
        Table appearance = Table.of(org.citydb.database.schema.Table.APPEARANCE.getName(), getSchema());

        Select select = Select.newInstance()
                .distinct(true)
                .select(appearance.column("theme"))
                .from(appearance);

        if (scope != FeatureScope.ALL) {
            joinFeatures(scope, select, appearance.column("feature_id"), Joins.LEFT_JOIN);
        }

        Set<String> themes = new HashSet<>();
        try (PreparedStatement stmt = connection.prepareStatement(select.toSql());
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                String theme = rs.getString(1);
                themes.add(theme != null ? theme : NULL_THEME);
            }
        }

        return themes;
    }

    public SurfaceDataInfo hasSurfaceData(FeatureScope scope) throws SQLException {
        try (Connection connection = adapter.getPool().getConnection(true)) {
            return hasSurfaceData(scope, connection);
        }
    }

    public SurfaceDataInfo hasSurfaceData(FeatureScope scope, Connection connection) throws SQLException {
        Table geometryData = Table.of(org.citydb.database.schema.Table.GEOMETRY_DATA.getName(), getSchema());
        Table surfaceDataMapping = Table.of(org.citydb.database.schema.Table.SURFACE_DATA_MAPPING.getName(),
                getSchema());

        Select template = Select.newInstance()
                .select(IntegerLiteral.of(1))
                .from(surfaceDataMapping)
                .join(geometryData).on(geometryData.column("id").eq(surfaceDataMapping.column("geometry_data_id")))
                .fetch(1);

        if (scope != FeatureScope.ALL) {
            joinFeatures(scope, template, geometryData.column("feature_id"), Joins.LEFT_JOIN);
        }

        Select select = Select.newInstance()
                .select(Case.newInstance()
                        .when(Exists.of(Select.of(template)
                                .where(surfaceDataMapping.column("material_mapping").isNotNull())))
                        .then(IntegerLiteral.of(1))
                        .orElse(IntegerLiteral.of(0)).as("material_mapping"))
                .select(Case.newInstance()
                        .when(Exists.of(Select.of(template)
                                .where(surfaceDataMapping.column("texture_mapping").isNotNull())))
                        .then(IntegerLiteral.of(1))
                        .orElse(IntegerLiteral.of(0)).as("texture_mapping"))
                .select(Case.newInstance()
                        .when(Exists.of(Select.of(template)
                                .where(surfaceDataMapping.column("georeferenced_texture_mapping").isNotNull())))
                        .then(IntegerLiteral.of(1))
                        .orElse(IntegerLiteral.of(0)).as("georeferenced_texture_mapping"));

        adapter.getSchemaAdapter().getDummyTable().ifPresent(select::from);

        try (PreparedStatement stmt = connection.prepareStatement(select.toSql());
             ResultSet rs = stmt.executeQuery()) {
            return rs.next() ?
                    new SurfaceDataInfo(rs.getBoolean(1), rs.getBoolean(2), rs.getBoolean(3)) :
                    new SurfaceDataInfo(false, false, false);
        }
    }

    public Map<FeatureType, Long> getSurfaceDataCount(FeatureScope scope) throws SQLException {
        try (Connection connection = adapter.getPool().getConnection(true)) {
            return getSurfaceDataCount(scope, connection);
        }
    }

    public Map<FeatureType, Long> getSurfaceDataCount(FeatureScope scope, Connection connection) throws SQLException {
        Table surfaceData = Table.of(org.citydb.database.schema.Table.SURFACE_DATA.getName(), getSchema());
        Column objectClassId = surfaceData.column("objectclass_id");

        Select select = Select.newInstance()
                .select(objectClassId, Function.of("count", surfaceData.column("id")))
                .from(surfaceData)
                .groupBy(objectClassId);

        if (scope != FeatureScope.ALL) {
            Table mapping = Table.of(org.citydb.database.schema.Table.SURFACE_DATA_MAPPING.getName(), getSchema());
            Table geometryData = Table.of(org.citydb.database.schema.Table.GEOMETRY_DATA.getName(), getSchema());

            Select inner = Select.newInstance()
                    .select(mapping.column("surface_data_id"))
                    .from(mapping)
                    .join(geometryData).on(geometryData.column("id").eq(mapping.column("geometry_data_id")));

            joinFeatures(scope, inner, geometryData.column("feature_id"), Joins.LEFT_JOIN);
            select.where(surfaceData.column("id").in(inner));
        }

        Map<FeatureType, Long> surfaceDataCount = new IdentityHashMap<>();
        try (PreparedStatement stmt = connection.prepareStatement(select.toSql());
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                FeatureType featureType = getSchemaMapping().getFeatureType(rs.getInt(1));
                surfaceDataCount.put(featureType, rs.getLong(2));
            }
        }

        return surfaceDataCount;
    }

    public boolean hasGlobalAppearances(FeatureScope scope) throws SQLException {
        try (Connection connection = adapter.getPool().getConnection(true)) {
            return hasGlobalAppearances(scope, connection);
        }
    }

    public boolean hasGlobalAppearances(FeatureScope scope, Connection connection) throws SQLException {
        Table appearance = Table.of(org.citydb.database.schema.Table.APPEARANCE.getName(), getSchema());

        Select select = Select.newInstance()
                .select(IntegerLiteral.of(1))
                .from(appearance)
                .where(appearance.column("feature_id").isNull()
                        .and(appearance.column("implicit_geometry_id").isNull()))
                .fetch(1);

        try (PreparedStatement stmt = connection.prepareStatement(select.toSql());
             ResultSet rs = stmt.executeQuery()) {
            return rs.next();
        }
    }

    public Map<String, Set<DataType>> getGenericAttributes(FeatureScope scope) throws SQLException {
        try (Connection connection = adapter.getPool().getConnection(true)) {
            return getGenericAttributes(scope, connection);
        }
    }

    public Map<String, Set<DataType>> getGenericAttributes(FeatureScope scope, Connection connection) throws SQLException {
        Table property = Table.of(org.citydb.database.schema.Table.PROPERTY.getName(), getSchema());

        Select select = Select.newInstance()
                .distinct(true)
                .select(property.columns("name", "datatype_id"))
                .from(property)
                .where(property.column("namespace_id")
                        .eq(getSchemaMapping().getNamespaceByURI(Namespaces.GENERICS).getId()));

        if (scope != FeatureScope.ALL) {
            joinFeatures(scope, select, property.column("feature_id"), Joins.INNER_JOIN);
        }

        Map<String, Set<DataType>> genericAttributes = new IdentityHashMap<>();
        try (PreparedStatement stmt = connection.prepareStatement(select.toSql());
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                DataType dataType = getSchemaMapping().getDataType(rs.getInt(2));
                genericAttributes.computeIfAbsent(rs.getString(1),
                                v -> Collections.newSetFromMap(new IdentityHashMap<>()))
                        .add(dataType);
            }
        }

        return genericAttributes;
    }

    public Pair<FeatureType, Long> getAddressCount(FeatureScope scope) throws SQLException {
        try (Connection connection = adapter.getPool().getConnection(true)) {
            return getAddressCount(scope, connection);
        }
    }

    public Pair<FeatureType, Long> getAddressCount(FeatureScope scope, Connection connection) throws SQLException {
        Table address = Table.of(org.citydb.database.schema.Table.ADDRESS.getName(), getSchema());

        Select select = Select.newInstance()
                .select(Function.of("count", address.column("id")))
                .from(address);

        if (scope != FeatureScope.ALL) {
            Table property = Table.of(org.citydb.database.schema.Table.PROPERTY.getName(), getSchema());

            Select inner = Select.newInstance()
                    .select(property.column("val_address_id"))
                    .from(property);

            joinFeatures(scope, inner, property.column("feature_id"), Joins.INNER_JOIN);
            select.where(address.column("id").in(inner));
        }

        try (PreparedStatement stmt = connection.prepareStatement(select.toSql());
             ResultSet rs = stmt.executeQuery()) {
            FeatureType featureType = getSchemaMapping().getFeatureType(Name.of("Address", Namespaces.CORE));
            return Pair.of(featureType, rs.next() ? rs.getLong(1) : 0);
        }
    }

    public DatabaseSize getDatabaseSize() throws DatabaseException, SQLException {
        try (Connection connection = adapter.getPool().getConnection(true)) {
            return getDatabaseSize(connection);
        }
    }

    private Map<String, Long> getCountByLod(String propertyColumn, FeatureScope scope, Connection connection) throws SQLException {
        Table property = Table.of(org.citydb.database.schema.Table.PROPERTY.getName(), getSchema());
        Column lod = property.column("val_lod");

        Select select = Select.newInstance()
                .select(lod, Function.of("count", property.column(propertyColumn)))
                .from(property)
                .where(lod.isNotNull())
                .groupBy(lod);

        if (scope != FeatureScope.ALL) {
            joinFeatures(scope, select, property.column("feature_id"), Joins.INNER_JOIN);
        }

        Map<String, Long> featureCount = new HashMap<>();
        try (PreparedStatement stmt = connection.prepareStatement(select.toSql());
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                featureCount.put(rs.getString(1), rs.getLong(2));
            }
        }

        return featureCount;
    }

    private void joinFeatures(FeatureScope scope, Select select, Column column, String joinType) {
        Table feature = Table.of(org.citydb.database.schema.Table.FEATURE.getName(), getSchema());
        Column terminationDate = feature.column("termination_date");

        select.join(Join.of(joinType, feature.column("id"), Operators.EQUAL_TO, column));

        switch (scope) {
            case ACTIVE -> select.where(terminationDate.isNull());
            case TERMINATED -> select.where(terminationDate.isNotNull());
        }
    }

    private SchemaMapping getSchemaMapping() {
        return adapter.getSchemaAdapter().getSchemaMapping();
    }

    private String getSchema() {
        return adapter.getConnectionDetails().getSchema();
    }
}
