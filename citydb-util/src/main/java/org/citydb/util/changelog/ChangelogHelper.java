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

package org.citydb.util.changelog;

import org.citydb.database.adapter.DatabaseAdapter;
import org.citydb.database.adapter.GeometryAdapter;
import org.citydb.database.geometry.GeometryException;
import org.citydb.database.schema.FeatureType;
import org.citydb.database.schema.SchemaMapping;
import org.citydb.database.srs.SpatialReference;
import org.citydb.database.srs.SrsException;
import org.citydb.model.change.FeatureChange;
import org.citydb.model.change.FeatureChangeDescriptor;
import org.citydb.model.change.TransactionType;
import org.citydb.model.geometry.Envelope;
import org.citydb.model.geometry.Geometry;
import org.citydb.util.changelog.query.ChangelogQuery;
import org.citydb.util.changelog.query.QueryBuildException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;

public class ChangelogHelper {
    private final DatabaseAdapter adapter;
    private final GeometryAdapter geometryAdapter;
    private final SchemaMapping schemaMapping;

    ChangelogHelper(DatabaseAdapter adapter) {
        this.adapter = adapter;
        geometryAdapter = adapter.getGeometryAdapter();
        schemaMapping = adapter.getSchemaAdapter().getSchemaMapping();
    }

    public FeatureChange getFeatureChange(ResultSet rs) throws ChangelogException, SQLException {
        long id = rs.getLong("id");
        FeatureType featureType = schemaMapping.getFeatureType(rs.getInt("objectclass_id"));
        return FeatureChange.of(featureType.getName())
                .setObjectId(rs.getString("objectid"))
                .setIdentifier(rs.getString("identifier"))
                .setIdentifierCodeSpace(rs.getString("identifier_codespace"))
                .setEnvelope(getEnvelope(rs.getObject("envelope")))
                .setTransactionType(TransactionType.fromDatabaseValue(rs.getString("transaction_type")))
                .setTransactionDate(rs.getObject("transaction_date", OffsetDateTime.class))
                .setDatabaseUser(rs.getString("db_user"))
                .setReasonForUpdate(rs.getString("reason_for_update"))
                .setDescriptor(FeatureChangeDescriptor.of(id, featureType.getId(), getFeatureId(rs)));
    }

    public Long getFeatureId(ResultSet rs) throws SQLException {
        long featureId = rs.getLong("feature_id");
        return rs.wasNull() ? null : featureId;
    }

    public Geometry<?> getGeometry(Object geometryObject) throws ChangelogException {
        try {
            return geometryObject != null ?
                    adapter.getGeometryAdapter().getGeometry(geometryObject) :
                    null;
        } catch (GeometryException e) {
            throw new ChangelogException("Failed to convert database geometry.", e);
        }
    }

    public Envelope getEnvelope(Object geometryObject) throws ChangelogException {
        Geometry<?> geometry = getGeometry(geometryObject);
        return geometry != null ? geometry.getEnvelope() : null;
    }

    public SpatialReference getTargetSrs(ChangelogQuery query) throws QueryBuildException {
        try {
            return geometryAdapter.getSpatialReference(query.getTargetSrs().orElse(null))
                    .orElse(adapter.getDatabaseMetadata().getSpatialReference());
        } catch (SrsException | SQLException e) {
            throw new QueryBuildException("The requested target SRS is not supported.", e);
        }
    }
}
