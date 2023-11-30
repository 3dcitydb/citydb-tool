/*
 * citydb-tool - Command-line tool for the 3D City Database
 * https://www.3dcitydb.org/
 *
 * Copyright 2022-2023
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

import org.citydb.core.concurrent.LazyInitializer;
import org.citydb.database.adapter.DatabaseAdapter;
import org.citydb.database.metadata.SpatialReferenceType;
import org.citydb.database.schema.Index;
import org.citydb.database.schema.Sequence;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

public class SchemaAdapter extends org.citydb.database.adapter.SchemaAdapter {
    private final LazyInitializer<String, IOException> featureHierarchyQuery;
    private final LazyInitializer<String, IOException> recursiveImplicitGeometryQuery;

    SchemaAdapter(DatabaseAdapter adapter) {
        super(adapter);
        featureHierarchyQuery = LazyInitializer.of(this::readFeatureHierarchyQuery);
        recursiveImplicitGeometryQuery = LazyInitializer.of(this::readRecursiveImplicitGeometryQuery);
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
            return featureHierarchyQuery.get();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to create feature hierarchy query.", e);
        }
    }

    @Override
    public String getRecursiveImplicitGeometryQuery(String featureQuery) {
        try {
            return recursiveImplicitGeometryQuery.get().replace("%FEATURE_QUERY%", featureQuery);
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
    protected String getCityDBVersion() {
        return "select major_version, minor_version, minor_revision, version from citydb_pkg.citydb_version()";
    }

    @Override
    protected String getSpatialReference() {
        return "select srid, coord_ref_sys_kind, coord_ref_sys_name, srs_name, wktext " +
                "from citydb_pkg.db_metadata()";
    }

    @Override
    protected SpatialReferenceType getSpatialReferenceType(String type) {
        switch (type.toUpperCase(Locale.ROOT)) {
            case "PROJCRS":
            case "PROJECTEDCRS":
            case "PROJCS":
                return SpatialReferenceType.PROJECTED_CRS;
            case "GEOGCRS":
            case "GEOGRAPHICCRS":
            case "GEOGCS":
                return SpatialReferenceType.GEOGRAPHIC_CRS;
            case "GEODCRS":
            case "GEODETICCRS":
            case "GEOCCS":
                return SpatialReferenceType.GEODETIC_CRS;
            case "COMPOUNDCRS":
            case "COMPDCS":
            case "COMPD_CS":
                return SpatialReferenceType.COMPOUND_CRS;
            case "ENGCRS":
            case "ENGINEERINGCRS":
            case "LOCAL_CS":
                return SpatialReferenceType.ENGINEERING_CRS;
            default:
                return SpatialReferenceType.UNKNOWN_CRS;
        }
    }

    private String readFeatureHierarchyQuery() throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(Objects.requireNonNull(
                SchemaAdapter.class.getResourceAsStream("/org/citydb/database/postgres/query_feature_hierarchy.sql"))))) {
            return reader.lines()
                    .collect(Collectors.joining(" "))
                    .replace("%SCHEMA%", adapter.getConnectionDetails().getSchema());
        }
    }

    public String readRecursiveImplicitGeometryQuery() throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(Objects.requireNonNull(
                SchemaAdapter.class.getResourceAsStream("/org/citydb/database/postgres/query_recursive_implicit_geometry.sql"))))) {
            return reader.lines()
                    .collect(Collectors.joining(" "))
                    .replace("%SCHEMA%", adapter.getConnectionDetails().getSchema());
        }
    }
}
