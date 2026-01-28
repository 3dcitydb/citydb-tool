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

package org.citydb.operation.exporter.feature;

import org.citydb.database.schema.FeatureType;
import org.citydb.model.feature.Feature;
import org.citydb.model.feature.FeatureDescriptor;
import org.citydb.operation.exporter.ExportException;
import org.citydb.operation.exporter.ExportHelper;
import org.citydb.operation.exporter.common.DatabaseExporter;
import org.citydb.sqlbuilder.literal.Placeholder;
import org.citydb.sqlbuilder.query.Select;
import org.citydb.sqlbuilder.schema.Table;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.*;

public class FeatureExporter extends DatabaseExporter {

    public FeatureExporter(ExportHelper helper) throws SQLException {
        super(helper);
        stmt = helper.getConnection().prepareStatement(getQuery().toSql());
    }

    private Select getQuery() {
        Table feature = tableHelper.getTable(org.citydb.database.schema.Table.FEATURE);
        return Select.newInstance()
                .select(feature.columns("id", "objectclass_id", "objectid", "identifier", "identifier_codespace",
                        "last_modification_date", "updating_person", "reason_for_update", "lineage",
                        "creation_date", "termination_date", "valid_from", "valid_to"))
                .select(helper.getTransformOperator(feature.column("envelope")))
                .from(feature)
                .where(operationHelper.inArray(feature.column("id"), Placeholder.empty()));
    }

    public Feature doExport(long id) throws ExportException, SQLException {
        setLongArrayOrNull(1, List.of(id));
        try (ResultSet rs = stmt.executeQuery()) {
            return doExport(rs).get(id);
        }
    }

    public Map<Long, Feature> doExport(Set<Long> ids) throws ExportException, SQLException {
        if (!ids.isEmpty()) {
            setLongArrayOrNull(1, ids);
            try (ResultSet rs = stmt.executeQuery()) {
                return doExport(rs);
            }
        } else {
            return Collections.emptyMap();
        }
    }

    private Map<Long, Feature> doExport(ResultSet rs) throws ExportException, SQLException {
        Map<Long, Feature> features = new HashMap<>();
        while (rs.next()) {
            long id = rs.getLong("id");
            FeatureType featureType = schemaMapping.getFeatureType(rs.getInt("objectclass_id"));
            features.put(id, Feature.of(featureType.getName())
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
                    .setDescriptor(FeatureDescriptor.of(id, featureType.getId())));
        }

        return features;
    }
}
