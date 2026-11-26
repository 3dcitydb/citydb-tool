/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.database.util;

import java.util.Optional;

public class StatementOptions {
    private Integer fetchSize;
    private int queryTimeout;

    private StatementOptions() {
    }

    private StatementOptions(StatementOptions options) {
        fetchSize = options.fetchSize;
        queryTimeout = options.queryTimeout;
    }

    public static StatementOptions defaults() {
        return new StatementOptions();
    }

    public static StatementOptions of(StatementOptions options) {
        return new StatementOptions(options);
    }

    public Optional<Integer> getFetchSize() {
        return Optional.ofNullable(fetchSize);
    }

    public StatementOptions fetchSize(int fetchSize) {
        this.fetchSize = Math.max(fetchSize, 0);
        return this;
    }

    public int getQueryTimeout() {
        return queryTimeout;
    }

    public StatementOptions queryTimeout(int queryTimeout) {
        this.queryTimeout = Math.max(queryTimeout, 0);
        return this;
    }
}
