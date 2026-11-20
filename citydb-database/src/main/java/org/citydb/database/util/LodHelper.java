/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.database.util;

import org.citydb.database.adapter.DatabaseAdapter;
import org.citydb.sqlbuilder.query.Select;
import org.citydb.sqlbuilder.schema.Table;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Optional;
import java.util.Set;

public abstract class LodHelper {
    protected final DatabaseAdapter adapter;

    protected LodHelper(DatabaseAdapter adapter) {
        this.adapter = adapter;
    }

    public abstract Select getBottomUpQuery(Set<String> lods);

    public abstract Select getTopDownQuery(Set<String> lods, Table table);

    public abstract long getSearchThreshold();

    public abstract Optional<LodStatistics> getStatistics(Connection connection) throws SQLException;
}
