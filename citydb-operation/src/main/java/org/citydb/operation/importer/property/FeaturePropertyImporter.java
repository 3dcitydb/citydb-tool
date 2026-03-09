/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.operation.importer.property;

import org.citydb.database.schema.Sequence;
import org.citydb.model.feature.Feature;
import org.citydb.model.property.FeatureProperty;
import org.citydb.model.property.PropertyDescriptor;
import org.citydb.operation.importer.ImportException;
import org.citydb.operation.importer.ImportHelper;
import org.citydb.operation.importer.feature.FeatureImporter;
import org.citydb.operation.importer.reference.CacheType;

import java.sql.SQLException;
import java.sql.Types;
import java.util.Collections;

public class FeaturePropertyImporter extends PropertyImporter {

    public FeaturePropertyImporter(ImportHelper helper) throws SQLException {
        super(helper);
    }

    @Override
    protected String getInsertStatement() {
        return "insert into " + tableHelper.getPrefixedTableName(table) +
                "(id, feature_id, parent_id, datatype_id, namespace_id, name, " +
                "val_feature_id, val_relation_type) " +
                "values (" + String.join(",", Collections.nCopies(8, "?")) + ")";
    }

    public PropertyDescriptor doImport(FeatureProperty property, long featureId) throws ImportException, SQLException {
        long propertyId = nextSequenceValue(Sequence.PROPERTY);
        return doImport(property, propertyId, propertyId, featureId);
    }

    PropertyDescriptor doImport(FeatureProperty property, long parentId, long featureId) throws ImportException, SQLException {
        return doImport(property, nextSequenceValue(Sequence.PROPERTY), parentId, featureId);
    }

    private PropertyDescriptor doImport(FeatureProperty property, long propertyId, long parentId, long featureId) throws ImportException, SQLException {
        Feature feature = property.getObject().orElse(null);
        if (feature != null && canImport(feature)) {
            stmt.setLong(7, tableHelper.getOrCreateImporter(FeatureImporter.class)
                    .doImport(feature)
                    .getId());
        } else {
            String reference = feature != null ?
                    feature.getOrCreateObjectId() :
                    property.getReference().orElseThrow(() -> new ImportException("The feature property " +
                            "contains neither an object nor a reference."));
            cacheReference(CacheType.FEATURE, reference, propertyId);
            stmt.setNull(7, Types.BIGINT);
        }

        stmt.setInt(8, property.getRelationType().getDatabaseValue());

        return super.doImport(property, propertyId, parentId, featureId);
    }
}
