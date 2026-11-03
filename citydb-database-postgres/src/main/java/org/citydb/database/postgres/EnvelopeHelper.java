/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.database.postgres;

import org.citydb.database.adapter.DatabaseAdapter;
import org.citydb.database.geometry.GeometryException;
import org.citydb.model.geometry.Envelope;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class EnvelopeHelper extends org.citydb.database.util.EnvelopeHelper {
    private final DatabaseAdapter adapter;
    private final String schema;
    private final PreparedStatement stmt;

    EnvelopeHelper(Connection connection, DatabaseAdapter adapter) throws SQLException {
        super(connection);
        this.adapter = adapter;
        schema = adapter.getConnectionDetails().getSchema();
        stmt = connection.prepareStatement("select citydb_pkg.get_feature_envelope(?, ?, ?, ?)");
    }

    @Override
    public Envelope getFeatureEnvelope(long id, Mode mode) throws GeometryException, SQLException {
        stmt.setLong(1, id);
        stmt.setString(2, schema);
        stmt.setInt(3, mode == Mode.RECOMPUTE || mode == Mode.RECOMPUTE_AND_STORE ? 1 : 0);
        stmt.setInt(4, mode == Mode.STORE_IF_MISSING || mode == Mode.RECOMPUTE_AND_STORE ? 1 : 0);

        try (ResultSet rs = stmt.executeQuery()) {
            return rs.next()
                    ? adapter.getGeometryAdapter().getEnvelope(rs.getObject(1))
                    : null;
        }
    }

    @Override
    public void updateFeatureEnvelope(long id) throws SQLException {
        stmt.setLong(1, id);
        stmt.setString(2, schema);
        stmt.setInt(3, 1);
        stmt.setInt(4, 1);

        try (ResultSet rs = stmt.executeQuery()) {
        }
    }

    @Override
    public void close() throws SQLException {
        stmt.close();
    }
}
