/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.database.postgres;

import org.citydb.database.adapter.DatabaseAdapter;
import org.citydb.database.geometry.GeometryException;
import org.citydb.database.geometry.WKBParser;
import org.citydb.database.geometry.WKBWriter;
import org.citydb.database.geometry.WKTWriter;
import org.citydb.database.schema.Table;
import org.citydb.model.geometry.Envelope;
import org.citydb.model.geometry.Geometry;
import org.postgresql.util.PGobject;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class GeometryAdapter extends org.citydb.database.adapter.GeometryAdapter {
    private final WKBParser parser = new WKBParser();
    private final BoxParser boxParser = new BoxParser();
    private final WKBWriter writer = new WKBWriter().includeSRID(true);
    private final WKTWriter textWriter = new WKTWriter().includeSRID(true);
    private final SpatialOperationHelper spatialOperationHelper;
    private final SrsHelper srsHelper;

    GeometryAdapter(DatabaseAdapter adapter) {
        super(adapter);
        spatialOperationHelper = new SpatialOperationHelper(adapter);
        srsHelper = new SrsHelper(adapter);
    }

    @Override
    public Geometry<?> getGeometry(Object geometryObject) throws GeometryException {
        return parser.parse(geometryObject);
    }

    @Override
    public Envelope getEnvelope(Object geometryObject) throws GeometryException {
        if (geometryObject instanceof PGobject object && object.getType().equals("geometry")) {
            Geometry<?> geometry = getGeometry(geometryObject);
            return geometry != null ? geometry.getEnvelope() : null;
        } else {
            return geometryObject != null ? boxParser.parse(geometryObject.toString()) : null;
        }
    }

    @Override
    public Object getGeometry(Geometry<?> geometry, boolean force3D, Connection connection) {
        return writer.write(geometry, force3D);
    }

    @Override
    public String getAsText(Geometry<?> geometry) throws GeometryException {
        return "'" + textWriter.write(geometry) + "'";
    }

    @Override
    public boolean hasImplicitGeometries(Connection connection) throws SQLException {
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("select 1 from " + adapter.getConnectionDetails().getSchema() +
                     "." + Table.IMPLICIT_GEOMETRY.getName() + " limit 1")) {
            return rs.next();
        }
    }

    @Override
    public SpatialOperationHelper getSpatialOperationHelper() {
        return spatialOperationHelper;
    }

    @Override
    public SrsHelper getSrsHelper() {
        return srsHelper;
    }
}
