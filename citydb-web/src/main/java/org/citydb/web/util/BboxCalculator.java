package org.citydb.web.util;

import org.citydb.database.adapter.DatabaseAdapter;
import org.citydb.database.geometry.GeometryException;
import org.citydb.model.feature.FeatureType;
import org.citydb.model.geometry.Envelope;
import org.citydb.model.geometry.Geometry;
import org.citydb.web.schema.Bbox;
import org.citydb.web.schema.Extent;
import org.citydb.web.schema.ExtentSpatial;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;

public class BboxCalculator {
    private final DatabaseConnector databaseConnector = DatabaseConnector.getInstance();
    private final CrsTransformer crsTransformer = new CrsTransformer();

    public BboxCalculator() {}

    public Extent getExtent(FeatureType featureType) throws SQLException {
        return getExtent(featureType, 4326);
    }

    public Extent getExtent(FeatureType featureType, Integer srid) throws SQLException {
        Envelope envelope = getEnvelope(featureType, srid);
        if (envelope != null) {
            return Extent.of(ExtentSpatial.of(Collections.singletonList(
                    Bbox.of(List.of(
                            BigDecimal.valueOf(envelope.getLowerCorner().getX()),
                            BigDecimal.valueOf(envelope.getLowerCorner().getY()),
                            BigDecimal.valueOf(envelope.getUpperCorner().getX()),
                            BigDecimal.valueOf(envelope.getUpperCorner().getY()))
                    )
            )));
        }

        return null;
    }

    public Envelope getEnvelope(FeatureType featureType, Integer srid) throws SQLException {
        DatabaseAdapter adapter = this.databaseConnector.getDatabaseManager().getAdapter();
        try {
            String schema = adapter.getConnectionDetails().getSchema();
            String query = "select ST_3DExtent(envelope)::geometry from " + schema + ".feature " +
                    "where objectclass_id = ? and termination_date is null";

            try (Connection connection = adapter.getPool().getConnection();
                 PreparedStatement psQuery = connection.prepareStatement(query)) {
                psQuery.setInt(1, adapter.getSchemaAdapter().getSchemaMapping()
                        .getFeatureType(featureType.getName()).getId());
                try (ResultSet rs = psQuery.executeQuery()) {
                    if (rs.next()) {
                        Object extentObj = rs.getObject(1);
                        if (!rs.wasNull()) {
                            Geometry<?> extentGeometry = adapter.getGeometryAdapter().getGeometry(extentObj);
                            Geometry<?> wgs84Extent = crsTransformer.transform(extentGeometry, srid, connection);
                            if (wgs84Extent != null) {
                                return wgs84Extent.getEnvelope();
                            }
                        }
                    }
                }
            }
        } catch (GeometryException | SQLException e) {
            throw new SQLException("Failed to calculate the envelope for '" + featureType.name() + "'.", e);
        }

        return null;
    }
}
