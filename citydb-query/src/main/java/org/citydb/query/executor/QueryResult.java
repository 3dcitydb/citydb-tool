/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.query.executor;

import org.citydb.core.cache.PersistentMapStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.Set;

public class QueryResult implements AutoCloseable {
    private final Logger logger = LoggerFactory.getLogger(QueryResult.class);
    private final Connection connection;
    private final PreparedStatement stmt;
    private final boolean deduplicateIds;
    private final Path tempDirectory;
    private final boolean closeConnection;
    private final ResultSet rs;
    private IdCache idCache;

    private record IdCache(Set<Long> distinctIds, PersistentMapStore store) {
    }

    QueryResult(Connection connection, PreparedStatement stmt, boolean deduplicateIds, Path tempDirectory, boolean closeConnection) throws SQLException {
        this.connection = connection;
        this.stmt = stmt;
        this.deduplicateIds = deduplicateIds;
        this.tempDirectory = tempDirectory;
        this.closeConnection = closeConnection;
        rs = stmt.executeQuery();
    }

    public boolean hasNext() throws SQLException {
        if (!rs.next()) {
            return false;
        }

        if (!deduplicateIds) {
            return true;
        }

        IdCache cache = getOrCreateIdCache();
        do {
            if (cache.distinctIds().add(getId())) {
                return true;
            }
        } while (rs.next());

        return false;
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

    private IdCache getOrCreateIdCache() throws SQLException {
        if (deduplicateIds && idCache == null) {
            try {
                PersistentMapStore store = PersistentMapStore.builder()
                        .tempDirectory(tempDirectory)
                        .build();
                idCache = new IdCache(Collections.newSetFromMap(store.getOrCreateMap("distinct-ids")), store);
                logger.debug("Initialized distinct id cache at {}.", store.getBackingFile());
            } catch (IOException e) {
                throw new SQLException("Failed to initialize distinct id cache.", e);
            }
        }

        return idCache;
    }

    @Override
    public void close() throws SQLException {
        try (Connection connection = closeConnection ? this.connection : null;
             PreparedStatement stmt = this.stmt;
             ResultSet rs = this.rs) {
        } finally {
            if (idCache != null) {
                idCache.store().close();
                idCache = null;
            }
        }
    }
}
