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
import org.citydb.database.metadata.DatabaseMetadata;
import org.citydb.database.metadata.DatabaseProperty;
import org.citydb.database.schema.SchemaException;
import org.citydb.database.schema.SchemaMapping;
import org.citydb.database.schema.SchemaMappingBuilder;
import org.citydb.database.util.*;
import org.citydb.sqlbuilder.common.SqlObject;
import org.citydb.sqlbuilder.query.Select;
import org.citydb.sqlbuilder.schema.Table;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public abstract class SchemaAdapter {
    protected final DatabaseAdapter adapter;
    private SchemaMapping schemaMapping;

    protected SchemaAdapter(DatabaseAdapter adapter) {
        this.adapter = adapter;
    }

    public abstract String getDefaultSchema();

    public abstract Optional<Table> getDummyTable();

    public abstract int getDefaultBatchSize();

    public abstract int getMaximumNumberOfItemsForInOperator();

    public abstract String getFeatureHierarchyQuery();

    public abstract SqlObject getRecursiveImplicitGeometryQuery(Select featureQuery);

    public abstract Select getRecursiveLodQuery(Set<String> lods, boolean requireAll, int searchDepth, Table table);

    public abstract SqlHelper getSqlHelper();

    public abstract OperationHelper getOperationHelper();

    public abstract IndexHelper getIndexHelper();

    public abstract SequenceHelper getSequenceHelper(Connection connection) throws SQLException;

    public abstract StatisticsHelper getStatisticsHelper();

    public abstract TempTableHelper getTempTableHelper();

    public abstract ChangelogHelper getChangelogHelper();

    protected abstract String getCityDBVersion();

    protected abstract boolean schemaExists(String schemaName, Version version, Connection connection) throws SQLException;

    void buildSchemaMapping() throws SchemaException {
        schemaMapping = SchemaMappingBuilder.newInstance().build(adapter);
    }

    public SchemaMapping getSchemaMapping() {
        return schemaMapping;
    }

    public boolean schemaExists(String schemaName) throws SQLException {
        try (Connection connection = adapter.getPool().getConnection()) {
            return schemaExists(schemaName, adapter.getDatabaseMetadata().getVersion(), connection);
        }
    }

    protected String getVendorProductString(DatabaseMetadata metadata) {
        String productString = metadata.getVendorProductName() + " " + metadata.getVendorProductVersion();
        if (metadata.hasProperties()) {
            productString += " (" + metadata.getProperties().values().stream()
                    .map(DatabaseProperty::toString)
                    .collect(Collectors.joining(", ")) + ")";
        }

        return productString;
    }

    protected List<DatabaseProperty> getDatabaseProperties(Version version, Connection connection) throws SQLException {
        return Collections.emptyList();
    }
}
