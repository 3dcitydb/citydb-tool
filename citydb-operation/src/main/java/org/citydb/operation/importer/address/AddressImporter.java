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

package org.citydb.operation.importer.address;

import com.alibaba.fastjson2.JSONArray;
import org.citydb.database.schema.Sequence;
import org.citydb.database.schema.Table;
import org.citydb.model.address.Address;
import org.citydb.model.address.AddressDescriptor;
import org.citydb.model.property.Value;
import org.citydb.operation.importer.ImportException;
import org.citydb.operation.importer.ImportHelper;
import org.citydb.operation.importer.common.DatabaseImporter;
import org.citydb.operation.importer.reference.CacheType;

import java.sql.SQLException;
import java.util.Collections;

public class AddressImporter extends DatabaseImporter {

    public AddressImporter(ImportHelper helper) throws SQLException {
        super(Table.ADDRESS, helper);
    }

    @Override
    protected String getInsertStatement() {
        return "insert into " + tableHelper.getPrefixedTableName(table) +
                "(id, objectid, identifier, identifier_codespace, street, house_number, po_box, zip_code, " +
                "city, state, country, free_text, multi_point, content, content_mime_type) " +
                "values (" + String.join(",", Collections.nCopies(15, "?")) + ")";
    }

    public AddressDescriptor doImport(Address address) throws ImportException, SQLException {
        long addressId = nextSequenceValue(Sequence.ADDRESS);
        String objectId = address.getObjectId().orElse(null);

        stmt.setLong(1, addressId);
        stmt.setString(2, objectId);
        setStringOrNull(3, address.getIdentifier().orElse(null));
        setStringOrNull(4, address.getIdentifierCodeSpace().orElse(null));
        setStringOrNull(5, address.getStreet().orElse(null));
        setStringOrNull(6, address.getHouseNumber().orElse(null));
        setStringOrNull(7, address.getPoBox().orElse(null));
        setStringOrNull(8, address.getZipCode().orElse(null));
        setStringOrNull(9, address.getCity().orElse(null));
        setStringOrNull(10, address.getState().orElse(null));
        setStringOrNull(11, address.getCountry().orElse(null));

        JSONArray arrayValue = address.getFreeText()
                .map(array -> new JSONArray(array.getValues().stream()
                        .map(Value::rawValue)
                        .toList()))
                .orElse(null);

        setJsonOrNull(12, getJson(arrayValue));
        setGeometryOrNull(13, getGeometry(address.getMultiPoint().orElse(null), true));
        setStringOrNull(14, address.getGenericContent().orElse(null));
        setStringOrNull(15, address.getGenericContentMimeType().orElse(null));

        addBatch();
        cacheTarget(CacheType.ADDRESS, objectId, addressId);

        AddressDescriptor descriptor = AddressDescriptor.of(addressId);
        address.setDescriptor(descriptor);
        return descriptor;
    }
}
