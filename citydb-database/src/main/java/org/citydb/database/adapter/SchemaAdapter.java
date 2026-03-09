/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
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

    protected abstract Version getCityDBVersion(Connection connection) throws SQLException;

    protected abstract boolean schemaExists(String schemaName, Version version, Connection connection) throws SQLException;

    void buildSchemaMapping() throws SchemaException {
        schemaMapping = SchemaMappingBuilder.newInstance().build(adapter);
    }

    public SchemaMapping getSchemaMapping() {
        return schemaMapping;
    }

    public boolean schemaExists(String schemaName, Connection connection) throws SQLException {
        return schemaExists(schemaName, adapter.getDatabaseMetadata().getVersion(), connection);
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
