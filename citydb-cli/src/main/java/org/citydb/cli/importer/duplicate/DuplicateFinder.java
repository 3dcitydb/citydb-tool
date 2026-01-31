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

package org.citydb.cli.importer.duplicate;

import org.citydb.database.adapter.DatabaseAdapter;
import org.citydb.database.adapter.SchemaAdapter;
import org.citydb.model.feature.Feature;
import org.citydb.sqlbuilder.literal.Placeholder;
import org.citydb.sqlbuilder.query.Select;
import org.citydb.sqlbuilder.schema.Table;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class DuplicateFinder {
    private final Map<String, Boolean> objectIds;
    private final Map<Long, Boolean> databaseIds;
    private final SchemaAdapter schemaAdapter;
    private final int batchSize;
    private final Connection connection;
    private final PreparedStatement stmt;
    private final Set<String> batches = new HashSet<>();

    DuplicateFinder(Map<String, Boolean> objectIds, Map<Long, Boolean> databaseIds, DatabaseAdapter adapter) throws SQLException {
        this.objectIds = objectIds;
        this.databaseIds = databaseIds;

        schemaAdapter = adapter.getSchemaAdapter();
        batchSize = Math.min(1000, schemaAdapter.getMaximumNumberOfItemsForInOperator());
        connection = adapter.getPool().getConnection();
        Table table = Table.of(org.citydb.database.schema.Table.FEATURE.getName(),
                adapter.getConnectionDetails().getSchema());
        Select select = Select.newInstance()
                .select(table.columns("objectid", "id"))
                .from(table)
                .where(schemaAdapter.getOperationHelper().inArray(table.column("objectid"), Placeholder.empty())
                        .and(table.column("termination_date").isNull()));
        stmt = connection.prepareStatement(select.toSql());
    }

    void process(Feature feature) throws SQLException {
        process(feature.getObjectId().orElse(null));
    }

    void process(String objectId) throws SQLException {
        if (objectId != null) {
            batches.add(objectId);
            if (batches.size() == batchSize) {
                executeBatch();
            }
        }
    }

    void process(DuplicateFinder other) throws SQLException {
        if (!other.batches.isEmpty()) {
            try {
                for (String objectId : other.batches) {
                    process(objectId);
                }
            } finally {
                other.batches.clear();
            }
        }
    }

    private void executeBatch() throws SQLException {
        if (!batches.isEmpty()) {
            try {
                schemaAdapter.getSqlHelper().setStringArrayOrNull(stmt, 1, batches);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        objectIds.put(rs.getString(1), Boolean.TRUE);
                        databaseIds.put(rs.getLong(2), Boolean.TRUE);
                    }
                }
            } finally {
                batches.clear();
            }
        }
    }

    void close() throws SQLException {
        try {
            executeBatch();
            stmt.close();
        } finally {
            connection.close();
        }
    }
}
