/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.operation.importer.property;

import org.citydb.database.schema.Sequence;
import org.citydb.model.address.Address;
import org.citydb.model.property.AddressProperty;
import org.citydb.model.property.PropertyDescriptor;
import org.citydb.operation.importer.ImportException;
import org.citydb.operation.importer.ImportHelper;
import org.citydb.operation.importer.address.AddressImporter;
import org.citydb.operation.importer.reference.CacheType;

import java.sql.SQLException;
import java.sql.Types;
import java.util.Collections;

public class AddressPropertyImporter extends PropertyImporter {

    public AddressPropertyImporter(ImportHelper helper) throws SQLException {
        super(helper);
    }

    @Override
    protected String getInsertStatement() {
        return "insert into " + tableHelper.getPrefixedTableName(table) +
                "(id, feature_id, parent_id, datatype_id, namespace_id, name, " +
                "val_address_id) " +
                "values (" + String.join(",", Collections.nCopies(7, "?")) + ")";
    }

    public PropertyDescriptor doImport(AddressProperty property, long featureId) throws ImportException, SQLException {
        long propertyId = nextSequenceValue(Sequence.PROPERTY);
        return doImport(property, propertyId, propertyId, featureId);
    }

    PropertyDescriptor doImport(AddressProperty property, long parentId, long featureId) throws ImportException, SQLException {
        return doImport(property, nextSequenceValue(Sequence.PROPERTY), parentId, featureId);
    }

    PropertyDescriptor doImport(AddressProperty property, long propertyId, long parentId, long featureId) throws ImportException, SQLException {
        Address address = property.getObject().orElse(null);
        if (address != null && canImport(address)) {
            stmt.setLong(7, tableHelper.getOrCreateImporter(AddressImporter.class)
                    .doImport(address)
                    .getId());
        } else {
            String reference = address != null ?
                    address.getOrCreateObjectId() :
                    property.getReference().orElseThrow(() -> new ImportException("The address property " +
                            "contains neither an object nor a reference."));
            cacheReference(CacheType.ADDRESS, reference, propertyId);
            stmt.setNull(7, Types.BIGINT);
        }

        return super.doImport(property, propertyId, parentId, featureId);
    }
}
