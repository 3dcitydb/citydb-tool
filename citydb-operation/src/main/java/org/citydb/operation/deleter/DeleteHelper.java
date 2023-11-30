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

package org.citydb.operation.deleter;

import org.citydb.database.adapter.DatabaseAdapter;
import org.citydb.operation.deleter.common.DatabaseDeleter;
import org.citydb.operation.deleter.feature.FeatureDeleter;
import org.citydb.operation.deleter.util.TableHelper;

import java.sql.Connection;
import java.sql.SQLException;

public class DeleteHelper {
    private final DatabaseAdapter adapter;
    private final DeleteOptions options;
    private final Connection connection;
    private final TableHelper tableHelper;

    DeleteHelper(DatabaseAdapter adapter, Connection connection, DeleteOptions options) {
        this.adapter = adapter;
        this.options = options;
        this.connection = connection;
        tableHelper = new TableHelper(this);
    }

    public DatabaseAdapter getAdapter() {
        return adapter;
    }

    public DeleteOptions getOptions() {
        return options;
    }

    public Connection getConnection() {
        return connection;
    }

    public TableHelper getTableHelper() {
        return tableHelper;
    }

    void deleteFeature(long id) throws DeleteException {
        try {
            tableHelper.getOrCreateDeleter(FeatureDeleter.class).deleteFeature(id);
        } catch (Exception e) {
            throw new DeleteException("Failed to delete feature (ID: " + id + ").", e);
        }
    }

    void executeBatch() throws DeleteException, SQLException {
        for (DatabaseDeleter deleter : tableHelper.getDeleters()) {
            deleter.executeBatch();
        }
    }

    void close() throws SQLException {
        for (DatabaseDeleter deleter : tableHelper.getDeleters()) {
            deleter.close();
        }
    }
}
