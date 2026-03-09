/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.operation.deleter.common;

import org.citydb.database.adapter.DatabaseAdapter;
import org.citydb.operation.deleter.DeleteException;
import org.citydb.operation.deleter.DeleteHelper;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public abstract class DatabaseDeleter {
    protected final DeleteHelper helper;
    protected final DatabaseAdapter adapter;
    protected final PreparedStatement stmt;
    private final Set<Long> batches = new HashSet<>();

    public DatabaseDeleter(DeleteHelper helper) throws SQLException {
        this.helper = helper;
        this.adapter = helper.getAdapter();
        stmt = getDeleteStatement(helper.getConnection());
    }

    protected abstract PreparedStatement getDeleteStatement(Connection connection) throws SQLException;

    protected abstract void executeBatch(Set<Long> ids) throws DeleteException, SQLException;

    protected void setJsonOrNull(int index, String json) throws SQLException {
        adapter.getSchemaAdapter().getSqlHelper().setJsonOrNull(stmt, index, json);
    }

    protected void setLongArrayOrNull(int index, Collection<Long> values) throws SQLException {
        adapter.getSchemaAdapter().getSqlHelper().setLongArrayOrNull(stmt, index, values);
    }

    protected void addBatch(long id) throws DeleteException, SQLException {
        batches.add(id);
        if (batches.size() == helper.getBatchSize()) {
            executeBatch();
        }
    }

    public void executeBatch() throws DeleteException, SQLException {
        if (!batches.isEmpty()) {
            try {
                executeBatch(batches);
            } finally {
                batches.clear();
            }
        }
    }

    public void close() throws SQLException {
        stmt.close();
    }
}
