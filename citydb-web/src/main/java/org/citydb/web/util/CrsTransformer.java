package org.citydb.web.util;

import org.citydb.database.adapter.DatabaseAdapter;
import org.citydb.database.geometry.GeometryException;
import org.citydb.model.geometry.Envelope;
import org.citydb.model.geometry.Geometry;
import org.citydb.model.geometry.Polygon;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class CrsTransformer {
    private final DatabaseConnector databaseConnector = DatabaseConnector.getInstance();

    public Envelope transform(Envelope envelope, Connection connection) throws SQLException, GeometryException {
        Polygon polygon = Polygon.of(envelope);
        envelope.getSRID().map(polygon::setSRID);

        Geometry<?> geometry = transform(polygon, connection);
        if (geometry != null) {
            return geometry.getEnvelope();
        }

        return null;
    }

    public Geometry<?> transform(Geometry<?> geometry, Connection connection) throws SQLException, GeometryException {
        DatabaseAdapter adapter = this.databaseConnector.getDatabaseManager().getAdapter();
        try (PreparedStatement psQuery = connection.prepareStatement("select ST_Transform(?, 4326)")) {
            Object unconverted = adapter.getGeometryAdapter().getGeometry(geometry);
            psQuery.setObject(1, unconverted, adapter.getGeometryAdapter().getGeometrySQLType());

            try (ResultSet rs = psQuery.executeQuery()) {
                if (rs.next()) {
                    Object converted = rs.getObject(1);
                    if (!rs.wasNull()) {
                        return adapter.getGeometryAdapter().getGeometry(converted);
                    }
                }
            }
        }

        return null;
    }
}
