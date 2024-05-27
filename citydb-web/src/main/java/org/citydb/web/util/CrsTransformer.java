package org.citydb.web.util;

import org.citydb.database.adapter.GeometryAdapter;
import org.citydb.database.geometry.GeometryException;
import org.citydb.model.geometry.Geometry;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class CrsTransformer {

    public static Geometry<?> transform(Geometry<?> geometry, Integer srid, Connection connection,
                                 GeometryAdapter geometryAdapter) throws SQLException, GeometryException {
        try (PreparedStatement psQuery = connection.prepareStatement("select ST_Transform(?, ?)")) {
            Object unconverted = geometryAdapter.getGeometry(geometry);
            psQuery.setObject(1, unconverted, geometryAdapter.getGeometrySQLType());
            psQuery.setInt(2, srid);
            try (ResultSet rs = psQuery.executeQuery()) {
                if (rs.next()) {
                    Object converted = rs.getObject(1);
                    if (!rs.wasNull()) {
                        return geometryAdapter.getGeometry(converted);
                    }
                }
            }
        }

        return null;
    }
}
