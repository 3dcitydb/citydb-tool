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

package org.citydb.cli.importer.duplicate;

import org.citydb.database.adapter.DatabaseAdapter;
import org.citydb.model.feature.Feature;
import org.citydb.sqlbuilder.literal.Placeholder;
import org.citydb.sqlbuilder.query.Select;
import org.citydb.sqlbuilder.schema.Table;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class DuplicateFinder {
    private final Map<String, Boolean> objectIds;
    private final Map<Long, Boolean> databaseIds;
    private final int batchSize;
    private final PreparedStatement stmt;
    private final Set<String> batches = new HashSet<>();

    DuplicateFinder(Map<String, Boolean> objectIds, Map<Long, Boolean> databaseIds, DatabaseAdapter adapter) throws SQLException {
        this.objectIds = objectIds;
        this.databaseIds = databaseIds;

        batchSize = adapter.getSchemaAdapter().getMaximumNumberOfItemsForInOperator();
        Table table = Table.of(org.citydb.database.schema.Table.FEATURE.getName(),
                adapter.getConnectionDetails().getSchema());
        Select select = Select.newInstance()
                .select(table.columns("objectid", "id"))
                .from(table)
                .where(table.column("objectid").in(Collections.nCopies(batchSize, Placeholder.empty()))
                        .and(table.column("termination_date").isNull()));
        stmt = adapter.getPool().getConnection().prepareStatement(select.toSql());
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
                int i = 1;
                for (String id : batches) {
                    stmt.setString(i++, id);
                }

                while (i <= batchSize) {
                    stmt.setNull(i++, Types.VARCHAR);
                }

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
        executeBatch();
        stmt.close();
    }
}
