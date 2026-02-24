/*
 * citydb-tool - Command-line tool for the 3D City Database
 * https://www.3dcitydb.org/
 *
 * Copyright 2022-2026
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

import org.citydb.database.adapter.DatabaseAdapter;
import org.citydb.database.schema.Index;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.stream.Collectors;

public class IndexHelper extends org.citydb.database.util.IndexHelper {

    protected IndexHelper(DatabaseAdapter adapter) {
        super(adapter);
    }

    @Override
    protected void createIndex(Index index, boolean ignoreNulls, Connection connection) throws SQLException {
        String sql = "create index if not exists " + index.getName() +
                " on " + adapter.getConnectionDetails().getSchema() + "." + index.getTable() +
                (index.getType() == Index.Type.SPATIAL ? " using gist " : " ") +
                "(" + String.join(", ", index.getColumns()) + ")";

        if (ignoreNulls) {
            sql += " where " + index.getColumns().stream()
                    .map(column -> column + " is not null")
                    .collect(Collectors.joining(" and "));
        }

        try (Statement stmt = connection.createStatement()) {
            stmt.execute(sql);
        }
    }

    @Override
    protected void dropIndex(Index index, Connection connection) throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("drop index if exists " + adapter.getConnectionDetails().getSchema() + "." + index.getName());
        }
    }

    @Override
    protected boolean indexExists(Index index, Connection connection) throws SQLException {
        String sql = "select 1 from pg_index i " +
                "join pg_class c on c.oid = i.indexrelid " +
                "join pg_namespace n on n.oid = c.relnamespace " +
                "where n.nspname = '" + adapter.getConnectionDetails().getSchema() + "' " +
                "and c.relname = '" + index.getName() + "' limit 1";

        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            return rs.next();
        }
    }
}
