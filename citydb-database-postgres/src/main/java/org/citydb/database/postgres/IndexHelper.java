/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
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
