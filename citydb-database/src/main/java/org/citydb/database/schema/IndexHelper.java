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

package org.citydb.database.schema;

import org.citydb.database.adapter.DatabaseAdapter;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class IndexHelper {
    public static final List<Index> DEFAULT_INDEXES = List.of(
            Index.FEATURE_OBJECTID,
            Index.FEATURE_IDENTIFIER,
            Index.FEATURE_ENVELOPE,
            Index.FEATURE_CREATION_DATE,
            Index.FEATURE_VALID_FROM,
            Index.FEATURE_VALID_TO,
            Index.GEOMETRY_DATA_GEOMETRY,
            Index.PROPERTY_NAME,
            Index.PROPERTY_VAL_TIMESTAMP,
            Index.PROPERTY_VAL_DOUBLE,
            Index.PROPERTY_VAL_INT,
            Index.PROPERTY_VAL_LOD,
            Index.PROPERTY_VAL_STRING,
            Index.PROPERTY_VAL_UOM,
            Index.PROPERTY_VAL_URI);

    public static final List<Index> DEFAULT_NORMAL_INDEXES = DEFAULT_INDEXES.stream()
            .filter(index -> index.getType() == Index.Type.NORMAL)
            .collect(Collectors.toList());
    public static final List<Index> DEFAULT_SPATIAL_INDEXES = DEFAULT_INDEXES.stream()
            .filter(index -> index.getType() == Index.Type.SPATIAL)
            .collect(Collectors.toList());

    public enum Status {
        ON,
        PARTIALLY_ON,
        OFF
    }

    private final DatabaseAdapter adapter;
    private Statement stmt;

    private IndexHelper(DatabaseAdapter adapter) {
        this.adapter = adapter;
    }

    public static IndexHelper newInstance(DatabaseAdapter adapter) {
        return new IndexHelper(adapter);
    }

    public void create(Index index) throws SQLException {
        try (Connection connection = adapter.getPool().getConnection()) {
            if (!exists(index, connection)) {
                try (Statement stmt = createStatement(connection)) {
                    stmt.executeUpdate(adapter.getSchemaAdapter().getCreateIndex(index));
                }
            }
        }
    }

    public void createAll(Index... indexes) throws SQLException {
        createAll(Arrays.asList(indexes));
    }

    public void createAll(Collection<Index> indexes) throws SQLException {
        for (Index index : indexes) {
            create(index);
        }
    }

    public void drop(Index index) throws SQLException {
        try (Connection connection = adapter.getPool().getConnection()) {
            if (exists(index, connection)) {
                try (Statement stmt = createStatement(connection)) {
                    stmt.executeUpdate(adapter.getSchemaAdapter().getDropIndex(index));
                }
            }
        }
    }

    public void dropAll(Index... indexes) throws SQLException {
        dropAll(Arrays.asList(indexes));
    }

    public void dropAll(Collection<Index> indexes) throws SQLException {
        for (Index index : indexes) {
            drop(index);
        }
    }

    public boolean exists(Index index) throws SQLException {
        try (Connection connection = adapter.getPool().getConnection()) {
            return exists(index, connection);
        }
    }

    public Status existAll(Index... indexes) throws SQLException {
        return existAll(Arrays.asList(indexes));
    }

    public Status existAll(Collection<Index> indexes) throws SQLException {
        Status result = null;
        for (Index index : indexes) {
            Status status = exists(index) ? Status.ON : Status.OFF;
            if (result == null) {
                result = status;
            } else if (result != status) {
                return Status.PARTIALLY_ON;
            }
        }

        return result != null ? result : Status.OFF;
    }

    private boolean exists(Index index, Connection connection) throws SQLException {
        try (Statement stmt = createStatement(connection);
             ResultSet rs = stmt.executeQuery(adapter.getSchemaAdapter().getIndexExists(index))) {
            return rs.next() && rs.getBoolean(1);
        }
    }

    private Statement createStatement(Connection connection) throws SQLException {
        return stmt = connection.createStatement();
    }

    public void cancel() {
        if (stmt != null) {
            try {
                stmt.cancel();
            } catch (SQLException e) {
                //
            }
        }
    }
}
