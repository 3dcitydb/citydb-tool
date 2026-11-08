/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.operation.deleter.common;

import org.citydb.database.adapter.DatabaseAdapter;
import org.citydb.operation.deleter.DeleteException;
import org.citydb.operation.deleter.DeleteHelper;

import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;

public abstract class DatabaseDeleter {
    protected final DeleteHelper helper;
    protected final DatabaseAdapter adapter;
    private final Set<Long> batches = new HashSet<>();

    public DatabaseDeleter(DeleteHelper helper) {
        this.helper = helper;
        this.adapter = helper.getAdapter();
    }

    protected abstract void executeBatch(Set<Long> ids) throws DeleteException, SQLException;

    public abstract void close() throws SQLException;

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
}
