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

package org.citydb.operation.deleter.common;

import org.citydb.operation.deleter.DeleteException;
import org.citydb.operation.deleter.DeleteHelper;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;

public abstract class DatabaseDeleter {
    protected final DeleteHelper helper;
    protected final PreparedStatement stmt;
    private final Set<Long> batches = new HashSet<>();

    public DatabaseDeleter(DeleteHelper helper) throws SQLException {
        this.helper = helper;
        stmt = getDeleteStatement(helper.getConnection());
    }

    protected abstract PreparedStatement getDeleteStatement(Connection connection) throws SQLException;

    protected abstract void executeBatch(Long[] ids) throws DeleteException, SQLException;

    protected void addBatch(long id) throws DeleteException, SQLException {
        batches.add(id);
        if (batches.size() == helper.getAdapter().getSchemaAdapter().getMaximumBatchSize()) {
            executeBatch();
        }
    }

    public void executeBatch() throws DeleteException, SQLException {
        if (!batches.isEmpty()) {
            try {
                executeBatch(batches.toArray(Long[]::new));
            } finally {
                batches.clear();
            }
        }
    }

    public void close() throws SQLException {
        stmt.close();
    }
}
