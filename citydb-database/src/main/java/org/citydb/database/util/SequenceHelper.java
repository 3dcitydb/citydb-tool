/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.database.util;

import org.citydb.database.schema.Sequence;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public abstract class SequenceHelper implements AutoCloseable {
    protected final Connection connection;

    protected SequenceHelper(Connection connection) {
        this.connection = connection;
    }

    protected abstract Map<Sequence, List<Long>> fetchNextValues(Map<Sequence, Integer> counts) throws SQLException;

    public final Map<Sequence, List<Long>> getNextValues(Map<Sequence, Integer> counts) throws SQLException {
        if (counts != null && !counts.isEmpty()) {
            Map<Sequence, List<Long>> values = fetchNextValues(counts);
            if (values != null) {
                return values;
            }
        }

        return Collections.emptyMap();
    }

    @Override
    public abstract void close() throws SQLException;
}
