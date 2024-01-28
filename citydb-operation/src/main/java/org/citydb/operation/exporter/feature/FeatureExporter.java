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

package org.citydb.operation.exporter.feature;

import org.citydb.database.schema.FeatureType;
import org.citydb.model.feature.Feature;
import org.citydb.model.feature.FeatureDescriptor;
import org.citydb.operation.exporter.ExportException;
import org.citydb.operation.exporter.ExportHelper;
import org.citydb.operation.exporter.common.DatabaseExporter;
import org.citydb.operation.exporter.hierarchy.HierarchyBuilder;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;

public class FeatureExporter extends DatabaseExporter {

    public FeatureExporter(ExportHelper helper) throws SQLException {
        super(helper);
        stmt = helper.getConnection().prepareStatement(adapter.getSchemaAdapter().getFeatureHierarchyQuery());
    }

    public Feature doExport(long id) throws ExportException, SQLException {
        stmt.setLong(1, id);
        try (ResultSet rs = stmt.executeQuery()) {
            return HierarchyBuilder.newInstance(id, helper)
                    .initialize(rs)
                    .build()
                    .getFeature(id);
        }
    }

    public Feature doExport(long id, ResultSet rs) throws ExportException, SQLException {
        FeatureType featureType = schemaMapping.getFeatureType(rs.getInt("objectclass_id"));
        return Feature.of(featureType.getName())
                .setObjectId(rs.getString("objectid"))
                .setIdentifier(rs.getString("identifier"))
                .setIdentifierCodeSpace(rs.getString("identifier_codespace"))
                .setEnvelope(getEnvelope(rs.getObject("envelope")))
                .setLastModificationDate(rs.getObject("last_modification_date", OffsetDateTime.class))
                .setUpdatingPerson(rs.getString("updating_person"))
                .setReasonForUpdate(rs.getString("reason_for_update"))
                .setLineage(rs.getString("lineage"))
                .setCreationDate(rs.getObject("creation_date", OffsetDateTime.class))
                .setTerminationDate(rs.getObject("termination_date", OffsetDateTime.class))
                .setValidFrom(rs.getObject("valid_from", OffsetDateTime.class))
                .setValidTo(rs.getObject("valid_to", OffsetDateTime.class))
                .setDescriptor(FeatureDescriptor.of(id, featureType.getId()));
    }
}
