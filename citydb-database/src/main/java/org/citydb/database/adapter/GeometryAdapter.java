/*
 * citydb-tool - Command-line tool for the 3D City Database
 * https://www.3dcitydb.org/
 *
 * Copyright 2022-2025
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

package org.citydb.database.adapter;

import com.alibaba.fastjson2.JSONObject;
import org.citydb.database.geometry.GeometryBuilder;
import org.citydb.database.geometry.GeometryException;
import org.citydb.database.geometry.PropertiesBuilder;
import org.citydb.database.util.SpatialOperationHelper;
import org.citydb.database.util.SrsHelper;
import org.citydb.model.geometry.Envelope;
import org.citydb.model.geometry.Geometry;
import org.citydb.sqlbuilder.literal.Placeholder;
import org.citydb.sqlbuilder.query.Select;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public abstract class GeometryAdapter {
    protected final DatabaseAdapter adapter;
    private final GeometryBuilder geometryBuilder = new GeometryBuilder();
    private final PropertiesBuilder propertiesBuilder = new PropertiesBuilder();

    protected GeometryAdapter(DatabaseAdapter adapter) {
        this.adapter = adapter;
    }

    public abstract Geometry<?> getGeometry(Object geometryObject) throws GeometryException;

    public abstract Envelope getEnvelope(Object geometryObject) throws GeometryException;

    public abstract Object getGeometry(Geometry<?> geometry, boolean force3D, Connection connection) throws GeometryException;

    public abstract String getAsText(Geometry<?> geometry) throws GeometryException;

    public abstract boolean hasImplicitGeometries(Connection connection) throws SQLException;

    public abstract SpatialOperationHelper getSpatialOperationHelper();

    public abstract SrsHelper getSrsHelper();

    public Object getGeometry(Geometry<?> geometry, Connection connection) throws GeometryException {
        return getGeometry(geometry, true, connection);
    }

    public Geometry<?> buildGeometry(Object geometryObject, JSONObject properties) throws GeometryException {
        return geometryObject != null ?
                geometryBuilder.buildGeometry(getGeometry(geometryObject), properties) :
                null;
    }

    public JSONObject buildGeometryProperties(Geometry<?> geometry) {
        return propertiesBuilder.buildProperties(geometry);
    }

    public <T extends Geometry<?>> T transform(T geometry) throws GeometryException, SQLException {
        return transform(geometry, adapter.getDatabaseMetadata().getSpatialReference().getSRID());
    }

    @SuppressWarnings("unchecked")
    public <T extends Geometry<?>> T transform(T geometry, int srid) throws GeometryException, SQLException {
        int sourceSRID = geometry.getSRID()
                .orElseThrow(() -> new GeometryException("The input geometry lacks an SRID."));
        if (sourceSRID != srid) {
            Select select = Select.newInstance()
                    .select(getSpatialOperationHelper().transform(Placeholder.of(geometry), srid));
            adapter.getSchemaAdapter().getDummyTable().ifPresent(select::from);

            try (Connection conn = adapter.getPool().getConnection();
                 PreparedStatement stmt = adapter.getSchemaAdapter().getSqlHelper().prepareStatement(select, conn);
                 ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    Geometry<?> result = buildGeometry(rs.getObject(1), buildGeometryProperties(geometry));
                    if (geometry.getClass().isInstance(result)) {
                        return (T) result.setSRID(srid);
                    }
                }

                throw new GeometryException("Failed to transform geometry to SRID " + srid + ".");
            }
        } else {
            return geometry;
        }
    }

    public Envelope transform(Envelope envelope) throws GeometryException, SQLException {
        return transform(envelope, adapter.getDatabaseMetadata().getSpatialReference().getSRID());
    }

    public Envelope transform(Envelope envelope, int srid) throws GeometryException, SQLException {
        return transform(envelope.convertToPolygon(), srid).getEnvelope();
    }

    public boolean hasImplicitGeometries() throws SQLException {
        try (Connection connection = adapter.getPool().getConnection()) {
            return hasImplicitGeometries(connection);
        }
    }
}
