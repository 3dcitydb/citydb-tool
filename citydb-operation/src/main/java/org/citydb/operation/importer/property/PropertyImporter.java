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
