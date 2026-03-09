/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.operation.importer.property;

import org.citydb.database.schema.Sequence;
import org.citydb.model.property.AppearanceProperty;
import org.citydb.model.property.PropertyDescriptor;
import org.citydb.operation.importer.ImportException;
import org.citydb.operation.importer.ImportHelper;
import org.citydb.operation.importer.appearance.AppearanceImporter;

import java.sql.SQLException;
import java.util.Collections;

public class AppearancePropertyImporter extends PropertyImporter {

    public AppearancePropertyImporter(ImportHelper helper) throws SQLException {
        super(helper);
    }

    @Override
    protected String getInsertStatement() {
        return "insert into " + tableHelper.getPrefixedTableName(table) +
                "(id, feature_id, parent_id, datatype_id, namespace_id, name, " +
                "val_appearance_id) " +
                "values (" + String.join(",", Collections.nCopies(7, "?")) + ")";
    }

    public PropertyDescriptor doImport(AppearanceProperty property, long featureId) throws ImportException, SQLException {
        long propertyId = nextSequenceValue(Sequence.PROPERTY);
        return doImport(property, propertyId, propertyId, featureId);
    }

    PropertyDescriptor doImport(AppearanceProperty property, long parentId, long featureId) throws ImportException, SQLException {
        return doImport(property, nextSequenceValue(Sequence.PROPERTY), parentId, featureId);
    }

    PropertyDescriptor doImport(AppearanceProperty property, long propertyId, long parentId, long featureId) throws ImportException, SQLException {
        stmt.setLong(7, tableHelper.getOrCreateImporter(AppearanceImporter.class)
                .doImport(property.getObject(), featureId, AppearanceImporter.Type.FEATURE)
                .getId());

        return super.doImport(property, propertyId, parentId, featureId);
    }
}
