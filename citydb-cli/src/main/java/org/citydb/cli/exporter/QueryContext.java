/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.cli.exporter;

import org.citydb.query.executor.QueryExecutor;
import org.citydb.util.tiling.Tile;

import java.sql.Connection;

public final class QueryContext {
    private final Tile tile;
    private final QueryExecutor queryExecutor;
    private final Connection connection;

    QueryContext(Tile tile, QueryExecutor queryExecutor, Connection connection) {
        this.tile = tile;
        this.queryExecutor = queryExecutor;
        this.connection = connection;
    }

    public Tile getTile() {
        return tile;
    }

    public QueryExecutor getQueryExecutor() {
        return queryExecutor;
    }

    public Connection getConnection() {
        return connection;
    }
}
