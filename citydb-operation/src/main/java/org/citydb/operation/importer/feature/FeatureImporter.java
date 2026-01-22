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

package org.citydb.operation.importer.feature;

import org.citydb.database.schema.Sequence;
import org.citydb.database.schema.Table;
import org.citydb.model.feature.Feature;
import org.citydb.model.feature.FeatureDescriptor;
import org.citydb.model.property.*;
import org.citydb.operation.importer.ImportException;
import org.citydb.operation.importer.ImportHelper;
import org.citydb.operation.importer.common.DatabaseImporter;
import org.citydb.operation.importer.options.CreationDateMode;
import org.citydb.operation.importer.property.*;
import org.citydb.operation.importer.reference.CacheType;

import java.sql.SQLException;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.Collections;

public class FeatureImporter extends DatabaseImporter {
    private final String updatingPerson;
    private final String reasonForUpdate;
    private final String lineage;
    private final CreationDateMode creationDateMode;
    private final OffsetDateTime creationDate;

    public FeatureImporter(ImportHelper helper) throws SQLException {
        super(Table.FEATURE, helper);
        updatingPerson = helper.getOptions().getUpdatingPerson()
                .orElse(helper.getAdapter().getConnectionDetails().getUser());
        reasonForUpdate = helper.getOptions().getReasonForUpdate().orElse(null);
        lineage = helper.getOptions().getLineage().orElse(null);
        creationDateMode = helper.getOptions().getCreationDateMode();
        creationDate = helper.getOptions().getCreationDate().orElse(null);
    }

    @Override
    protected String getInsertStatement() {
        return "insert into " + tableHelper.getPrefixedTableName(table) +
                "(id, objectclass_id, objectid, identifier, identifier_codespace, envelope, last_modification_date, " +
                "updating_person, reason_for_update, lineage, creation_date, termination_date, valid_from, valid_to) " +
                "values (" + String.join(",", Collections.nCopies(14, "?")) + ")";
    }

    public FeatureDescriptor doImport(Feature feature) throws ImportException, SQLException {
        long featureId = nextSequenceValue(Sequence.FEATURE);
        int objectClassId = schemaMapping.getFeatureType(feature.getFeatureType()).getId();
        String objectId = feature.getObjectId().orElse(null);
        OffsetDateTime importTime = getImportTime();

        stmt.setLong(1, featureId);
        stmt.setInt(2, objectClassId);
        stmt.setString(3, feature.getOrCreateObjectId());
        setStringOrNull(4, feature.getIdentifier().orElse(null));
        setStringOrNull(5, feature.getIdentifierCodeSpace().orElse(null));
        setGeometryOrNull(6, getEnvelope(feature.getEnvelope().orElse(null)));
        stmt.setObject(7, feature.getLastModificationDate().orElse(importTime), Types.TIMESTAMP_WITH_TIMEZONE);
        stmt.setString(8, feature.getUpdatingPerson().orElse(updatingPerson));
        setStringOrNull(9, feature.getReasonForUpdate().orElse(reasonForUpdate));
        setStringOrNull(10, feature.getLineage().orElse(lineage));

        OffsetDateTime creationDate = switch (creationDateMode) {
            case OVERWRITE_WITH_FIXED -> this.creationDate != null ? this.creationDate : importTime;
            case OVERWRITE_WITH_NOW -> importTime;
            default -> feature.getCreationDate().orElse(importTime);
        };

        stmt.setObject(11, creationDate, Types.TIMESTAMP_WITH_TIMEZONE);
        setTimestampOrNull(12, feature.getTerminationDate().orElse(null));
        setTimestampOrNull(13, feature.getValidFrom().orElse(null));
        setTimestampOrNull(14, feature.getValidTo().orElse(null));

        addBatch();
        cacheTarget(CacheType.FEATURE, objectId, featureId);

        if (feature.hasAttributes()) {
            for (Attribute attribute : feature.getAttributes().getAll()) {
                tableHelper.getOrCreateImporter(AttributeImporter.class).doImport(attribute, featureId);
            }
        }

        if (feature.hasGeometries()) {
            for (GeometryProperty property : feature.getGeometries().getAll()) {
                tableHelper.getOrCreateImporter(GeometryPropertyImporter.class).doImport(property, featureId);
            }
        }

        if (feature.hasImplicitGeometries()) {
            for (ImplicitGeometryProperty property : feature.getImplicitGeometries().getAll()) {
                tableHelper.getOrCreateImporter(ImplicitGeometryPropertyImporter.class).doImport(property, featureId);
            }
        }

        if (feature.hasFeatures()) {
            for (FeatureProperty property : feature.getFeatures().getAll()) {
                tableHelper.getOrCreateImporter(FeaturePropertyImporter.class).doImport(property, featureId);
            }
        }

        if (feature.hasAppearances()) {
            for (AppearanceProperty property : feature.getAppearances().getAll()) {
                tableHelper.getOrCreateImporter(AppearancePropertyImporter.class).doImport(property, featureId);
            }
        }

        if (feature.hasAddresses()) {
            for (AddressProperty property : feature.getAddresses().getAll()) {
                tableHelper.getOrCreateImporter(AddressPropertyImporter.class).doImport(property, featureId);
            }
        }

        FeatureDescriptor descriptor = FeatureDescriptor.of(featureId, objectClassId);
        feature.setDescriptor(descriptor);
        return descriptor;
    }
}
