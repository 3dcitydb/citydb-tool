/*
 * citydb-tool - Command-line tool for the 3D City Database
 * https://www.3dcitydb.org/
 *
 * Copyright 2022-2023
 * Virtual City Systems, Germany
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

package org.citydb.database;

import java.sql.Connection;
import java.sql.SQLException;

public class Pool {
    private final DatabaseManager manager;

    private Pool(DatabaseManager manager) {
        this.manager = manager;
    }

    static Pool newInstance(DatabaseManager manager) {
        return new Pool(manager);
    }

    public Connection getConnection(boolean autoCommit) throws SQLException {
        if (manager.isConnected()) {
            Connection connection = manager.getDataSource().getConnection();
            connection.setAutoCommit(autoCommit);
            return connection;
        } else {
            throw new SQLException("No database connection established.");
        }
    }

    public Connection getConnection() throws SQLException {
        return getConnection(true);
    }
}
