/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.database.util;

import tools.jackson.databind.node.ObjectNode;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Set;

public abstract class DeleteHelper implements AutoCloseable {
    protected final Connection connection;

    protected DeleteHelper(Connection connection) {
        this.connection = connection;
    }

    public abstract boolean deleteFeature(long id) throws SQLException;

    public abstract Set<Long> deleteFeatures(Collection<Long> ids) throws SQLException;

    public abstract boolean terminateFeature(long id, ObjectNode metadata) throws SQLException;

    public abstract Set<Long> terminateFeatures(Collection<Long> ids, ObjectNode metadata) throws SQLException;

    @Override
    public abstract void close() throws SQLException;
}
