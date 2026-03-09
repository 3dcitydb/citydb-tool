/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.operation.importer.property;

import org.citydb.database.schema.Table;
import org.citydb.model.property.Property;
import org.citydb.model.property.PropertyDescriptor;
import org.citydb.operation.importer.ImportHelper;
import org.citydb.operation.importer.common.DatabaseImporter;

import java.sql.SQLException;
import java.sql.Types;

public abstract class PropertyImporter extends DatabaseImporter {

    public PropertyImporter(ImportHelper helper) throws SQLException {
        super(Table.PROPERTY, helper);
    }

    PropertyDescriptor doImport(Property<?> property, long propertyId, long parentId, long featureId) throws SQLException {
        stmt.setLong(1, propertyId);
        stmt.setLong(2, featureId);

        if (parentId != propertyId) {
            stmt.setLong(3, parentId);
        } else {
            stmt.setNull(3, Types.BIGINT);
        }

        stmt.setInt(4, schemaMapping.getDataType(property.getDataType().orElse(null)).getId());
        setIntegerOrNull(5, schemaMapping.getNamespaceByURI(property.getName().getNamespace()).getId());
        stmt.setString(6, property.getName().getLocalName());

        addBatch();

        PropertyDescriptor descriptor = PropertyDescriptor.of(propertyId, featureId)
                .setParentId(parentId != propertyId ? parentId : 0);

        property.setDescriptor(descriptor);
        return descriptor;
    }
}
