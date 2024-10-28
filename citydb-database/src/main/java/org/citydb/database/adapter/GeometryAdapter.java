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

package org.citydb.database.adapter;

import com.alibaba.fastjson2.JSONObject;
import org.citydb.config.common.SrsReference;
import org.citydb.database.geometry.GeometryBuilder;
import org.citydb.database.geometry.GeometryException;
import org.citydb.database.geometry.PropertiesBuilder;
import org.citydb.database.srs.SpatialReference;
import org.citydb.database.srs.SrsException;
import org.citydb.database.util.SpatialOperationHelper;
import org.citydb.database.util.SrsHelper;
import org.citydb.model.geometry.Envelope;
import org.citydb.model.geometry.Geometry;
import org.citydb.sqlbuilder.literal.Placeholder;
import org.citydb.sqlbuilder.query.Select;

import java.sql.*;
import java.util.Optional;

public abstract class GeometryAdapter {
    protected final DatabaseAdapter adapter;
    private final GeometryBuilder geometryBuilder = new GeometryBuilder();
    private final PropertiesBuilder propertiesBuilder = new PropertiesBuilder();
    private final SrsHelper srsHelper;

    protected GeometryAdapter(DatabaseAdapter adapter) {
        this.adapter = adapter;
        srsHelper = SrsHelper.newInstance(adapter);
    }

    public abstract int getGeometrySqlType();

    public abstract String getGeometryTypeName();

    public abstract Geometry<?> getGeometry(Object geometryObject) throws GeometryException;

    public abstract Envelope getEnvelope(Object geometryObject) throws GeometryException;

    public abstract Object getGeometry(Geometry<?> geometry, boolean force3D) throws GeometryException;

    public abstract String getAsText(Geometry<?> geometry) throws GeometryException;

    public abstract boolean hasImplicitGeometries(Connection connection) throws SQLException;

    public abstract SpatialOperationHelper getSpatialOperationHelper();

    public SrsHelper getSrsHelper() {
        return srsHelper;
    }

    public Object getGeometry(Geometry<?> geometry) throws GeometryException {
        return getGeometry(geometry, true);
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

    public SpatialReference getSpatialReference(int srid) throws SrsException, SQLException {
        return getSpatialReference(srid, null);
    }

    public SpatialReference getSpatialReference(String identifier) throws SrsException, SQLException {
        return getSpatialReference(srsHelper.parseSRID(identifier), identifier);
    }

    public SpatialReference getSpatialReference(int srid, String identifier) throws SrsException, SQLException {
        SpatialReference databaseSrs = adapter.getDatabaseMetadata().getSpatialReference();
        if (srid == databaseSrs.getSRID()) {
            return databaseSrs.getIdentifier().equals(identifier) ?
                    databaseSrs :
                    SpatialReference.of(srid,
                            databaseSrs.getType(),
                            databaseSrs.getName(),
                            identifier,
                            databaseSrs.getWKT());
        } else {
            try (Connection connection = adapter.getPool().getConnection();
                 Statement statement = connection.createStatement();
                 ResultSet rs = statement.executeQuery(adapter.getSchemaAdapter().getSpatialReference(srid))) {
                if (rs.next()) {
                    return SpatialReference.of(srid,
                            adapter.getSchemaAdapter().getSpatialReferenceType(rs.getString("coord_ref_sys_kind")),
                            rs.getString("coord_ref_sys_name"),
                            identifier,
                            rs.getString("wktext"));
                }
            }

            throw new SrsException("The SRID " + srid + " is not supported by the database.");
        }
    }

    public Optional<SpatialReference> getSpatialReference(SrsReference reference) throws SrsException, SQLException {
        if (reference != null) {
            if (reference.getSRID().isPresent()) {
                return Optional.of(getSpatialReference(reference.getSRID().get(),
                        reference.getIdentifier().orElse(null)));
            } else if (reference.getIdentifier().isPresent()) {
                return Optional.of(getSpatialReference(reference.getIdentifier().get()));
            }
        }

        return Optional.empty();
    }

    public Optional<SpatialReference> getSpatialReference(org.citydb.model.geometry.SrsReference reference) throws SrsException, SQLException {
        if (reference != null) {
            if (reference.getSRID().isPresent()) {
                return Optional.of(getSpatialReference(reference.getSRID().get(),
                        reference.getSrsIdentifier().orElse(null)));
            } else if (reference.getSrsIdentifier().isPresent()) {
                return Optional.of(getSpatialReference(reference.getSrsIdentifier().get()));
            }
        }

        return Optional.empty();
    }
}
