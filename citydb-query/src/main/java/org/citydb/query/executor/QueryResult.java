/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.query.executor;

import org.citydb.core.cache.PersistentMapStore;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.Set;

public class QueryResult implements AutoCloseable {
    private final Connection connection;
    private final PreparedStatement stmt;
    private final PersistentMapStore store;
    private final ResultSet rs;
    private Set<Long> distinctIds;

    QueryResult(Connection connection, PreparedStatement stmt, PersistentMapStore store) throws SQLException {
        this.connection = connection;
        this.stmt = stmt;
        this.store = store;
        rs = stmt.executeQuery();
        distinctIds = store != null ? Collections.newSetFromMap(store.getOrCreateMap("distinct-ids")) : null;
    }

    public boolean hasNext() throws SQLException {
        if (distinctIds != null) {
            while (rs.next()) {
                if (distinctIds.add(getId())) {
                    return true;
                }
            }

            return false;
        } else {
            return rs.next();
        }
    }

    public long getId() throws SQLException {
        return rs.getLong("id");
    }

    public int getObjectClassId() throws SQLException {
        return rs.getInt("objectclass_id");
    }

    public <R> R get(SqlFunction<R> getter) throws SQLException {
        return getter.apply(rs);
    }

    @Override
    public void close() throws SQLException {
        try {
            connection.close();
            stmt.close();
            rs.close();
        } finally {
            if (store != null) {
                store.close();
                distinctIds = null;
            }
        }
    }
}
