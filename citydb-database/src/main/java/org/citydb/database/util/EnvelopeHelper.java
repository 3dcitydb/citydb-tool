/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.database.util;

import org.citydb.database.geometry.GeometryException;
import org.citydb.model.geometry.Envelope;

import java.sql.Connection;
import java.sql.SQLException;

public abstract class EnvelopeHelper implements AutoCloseable {
    protected final Connection connection;

    public enum Mode {
        USE_STORED_OR_COMPUTE,
        STORE_IF_MISSING,
        RECOMPUTE,
        RECOMPUTE_AND_STORE
    }

    protected EnvelopeHelper(Connection connection) {
        this.connection = connection;
    }

    public abstract Envelope getFeatureEnvelope(long id, Mode mode) throws GeometryException, SQLException;

    public abstract void updateFeatureEnvelope(long id) throws SQLException;

    @Override
    public abstract void close() throws SQLException;
}
