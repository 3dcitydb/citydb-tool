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

package org.citydb.query.executor;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class QueryResult implements AutoCloseable {
    private final Connection connection;
    private final PreparedStatement stmt;
    private final ResultSet rs;

    QueryResult(Connection connection, PreparedStatement stmt, ResultSet rs) {
        this.connection = connection;
        this.stmt = stmt;
        this.rs = rs;
    }

    public boolean hasNext() throws SQLException {
        return rs.next();
    }

    public long getId() throws SQLException {
        return rs.getLong("id");
    }

    public int getObjectClassId() throws SQLException {
        return rs.getInt("objectclass_id");
    }

    @Override
    public void close() throws SQLException {
        connection.close();
        stmt.close();
        rs.close();
    }
}
