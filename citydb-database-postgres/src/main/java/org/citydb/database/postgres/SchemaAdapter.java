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

package org.citydb.database.postgres;

import org.citydb.core.concurrent.LazyCheckedInitializer;
import org.citydb.database.adapter.DatabaseAdapter;
import org.citydb.database.metadata.SpatialReferenceType;
import org.citydb.database.schema.Index;
import org.citydb.database.schema.Sequence;
import org.citydb.sqlbuilder.common.SqlObject;
import org.citydb.sqlbuilder.query.Select;
import org.citydb.sqlbuilder.util.PlainText;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

public class SchemaAdapter extends org.citydb.database.adapter.SchemaAdapter {
    private final LazyCheckedInitializer<String, IOException> featureHierarchyQuery;
    private final LazyCheckedInitializer<String, IOException> recursiveImplicitGeometryQuery;
    private final OperationHelper operationHelper;

    SchemaAdapter(DatabaseAdapter adapter) {
        super(adapter);
        featureHierarchyQuery = LazyCheckedInitializer.of(this::readFeatureHierarchyQuery);
        recursiveImplicitGeometryQuery = LazyCheckedInitializer.of(this::readRecursiveImplicitGeometryQuery);
        operationHelper = new OperationHelper(this);
    }

    @Override
    public String getDefaultSchema() {
        return "citydb";
    }

    @Override
    public String getNextSequenceValues(Sequence sequence) {
        return "select citydb_pkg.get_seq_values('" + adapter.getConnectionDetails().getSchema() + "." +
                sequence + "', ?)";
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
            return PlainText.of(featureHierarchyQuery.get(),
                    "F.ENVELOPE",
                    "G.GEOMETRY",
                    "A.MULTI_POINT").toString();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to create feature hierarchy query.", e);
        }
    }

    @Override
    public String getFeatureHierarchyQuery(int targetSRID) {
        try {
            return PlainText.of(featureHierarchyQuery.get(),
                    "st_transform(F.ENVELOPE, " + targetSRID + ")",
                    "st_transform(G.GEOMETRY, " + targetSRID + ")",
                    "st_transform(A.MULTI_POINT, " + targetSRID + ")").toString();
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
    public String getCreateIndex(Index index) {
        return "create index if not exists " + index.getName() +
                " on " + adapter.getConnectionDetails().getSchema() + "." + index.getTable().getName() +
                (index.getType() == Index.Type.SPATIAL ? " using gist " : " ") +
                "(" + String.join(", ", index.getColumns()) + ")";
    }

    @Override
    public String getDropIndex(Index index) {
        return "drop index if exists " + adapter.getConnectionDetails().getSchema() + "." + index.getName();
    }

    @Override
    public String getIndexExists(Index index) {
        return "select 1 from pg_index i " +
                "join pg_class c on c.oid = i.indexrelid " +
                "join pg_namespace n on n.oid = c.relnamespace " +
                "where n.nspname = '" + adapter.getConnectionDetails().getSchema() + "' " +
                "and c.relname = '" + index.getName() + "' limit 1";
    }

    @Override
    public OperationHelper getOperationHelper() {
        return operationHelper;
    }

    @Override
    protected String getCityDBVersion() {
        return "select major_version, minor_version, minor_revision, version from citydb_pkg.citydb_version()";
    }

    @Override
    protected String getDatabaseSrs() {
        return "select srid, coord_ref_sys_kind, coord_ref_sys_name, srs_name, wktext " +
                "from citydb_pkg.db_metadata()";
    }

    @Override
    protected String getSpatialReference(int srid) {
        return "select split_part(srtext, '[', 1) as coord_ref_sys_kind, " +
                "split_part(srtext, '\"', 2) as coord_ref_sys_name, " +
                "srtext as wktext from spatial_ref_sys where srid = " + srid;
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

    private String readFeatureHierarchyQuery() throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(Objects.requireNonNull(
                SchemaAdapter.class.getClassLoader().getResourceAsStream("org/citydb/database/postgres/query_feature_hierarchy.sql"))))) {
            return reader.lines()
                    .collect(Collectors.joining(" "))
                    .replace("%SCHEMA%", adapter.getConnectionDetails().getSchema());
        }
    }

    private String readRecursiveImplicitGeometryQuery() throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(Objects.requireNonNull(
                SchemaAdapter.class.getResourceAsStream("/org/citydb/database/postgres/query_recursive_implicit_geometry.sql"))))) {
            return reader.lines()
                    .collect(Collectors.joining(" "))
                    .replace("%SCHEMA%", adapter.getConnectionDetails().getSchema());
        }
    }
}
