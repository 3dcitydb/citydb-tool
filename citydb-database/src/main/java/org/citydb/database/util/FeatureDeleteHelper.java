/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.database.util;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collection;

public abstract class FeatureDeleteHelper implements AutoCloseable {
    protected final Connection connection;

    protected FeatureDeleteHelper(Connection connection) {
        this.connection = connection;
    }

    public abstract void deleteFeatures(Collection<Long> ids) throws SQLException;

    public abstract void terminateFeatures(Collection<Long> ids, String metadata, boolean cascade) throws SQLException;

    @Override
    public abstract void close() throws SQLException;
}
