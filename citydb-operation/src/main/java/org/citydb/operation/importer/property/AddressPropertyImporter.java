/*
 * citydb-tool - Command-line tool for the 3D City Database
 * https://www.3dcitydb.org/
 *
 * Copyright 2022-2023
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

import org.citydb.database.schema.Sequence;
import org.citydb.model.address.Address;
import org.citydb.model.common.Reference;
import org.citydb.model.common.RelationType;
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
                "val_address_id, val_reference_type) " +
                "values (" + String.join(",", Collections.nCopies(7, "?")) + ", " +
                RelationType.CONTAINS.getDatabaseValue() + ")";
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
        if (address != null) {
            stmt.setLong(7, tableHelper.getOrCreateImporter(AddressImporter.class)
                    .doImport(address)
                    .getId());
        } else if (property.getReference().isPresent()) {
            Reference reference = property.getReference().get();
            cacheReference(CacheType.ADDRESS, reference, propertyId);
            stmt.setNull(7, Types.BIGINT);
        }

        return super.doImport(property, propertyId, parentId, featureId);
    }
}
