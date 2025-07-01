/*
 * citydb-tool - Command-line tool for the 3D City Database
 * https://www.3dcitydb.org/
 *
 * Copyright 2022-2024
 * virtualcitysystems GmbH, Germany
 * https://vc.systems/
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.citydb.database.postgres;

import org.citydb.database.adapter.DatabaseAdapter;
import org.citydb.database.geometry.GeometryException;
import org.citydb.database.geometry.WKBParser;
import org.citydb.database.geometry.WKBWriter;
import org.citydb.database.geometry.WKTWriter;
import org.citydb.model.geometry.Envelope;
import org.citydb.model.geometry.Geometry;
import org.postgresql.util.PGobject;

import java.sql.*;

public class GeometryAdapter extends org.citydb.database.adapter.GeometryAdapter {
    private final WKBParser parser = new WKBParser();
    private final BoxParser boxParser = new BoxParser();
    private final WKBWriter writer = new WKBWriter().includeSRID(true);
    private final WKTWriter textWriter = new WKTWriter().includeSRID(true);
    private final SpatialOperationHelper spatialOperationHelper = new SpatialOperationHelper();

    GeometryAdapter(DatabaseAdapter adapter) {
        super(adapter);
    }

    @Override
    public int getGeometrySqlType() {
        return Types.OTHER;
    }

    @Override
    public String getGeometryTypeName() {
        return "ST_Geometry";
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
    public Object getGeometry(Geometry<?> geometry, boolean force3D) {
        return writer.write(geometry, force3D);
    }

    @Override
    public String getAsText(Geometry<?> geometry) throws GeometryException {
        return textWriter.write(geometry);
    }

    @Override
    public boolean hasImplicitGeometries(Connection connection) throws SQLException {
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("select 1 from " + adapter.getConnectionDetails().getSchema() +
                     ".implicit_geometry limit 1")) {
            return rs.next();
        }
    }

    @Override
    public SpatialOperationHelper getSpatialOperationHelper() {
        return spatialOperationHelper;
    }
}
