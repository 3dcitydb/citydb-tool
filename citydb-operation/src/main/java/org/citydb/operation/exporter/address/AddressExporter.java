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

package org.citydb.operation.exporter.address;

import org.citydb.model.address.Address;
import org.citydb.model.address.AddressDescriptor;
import org.citydb.model.geometry.MultiPoint;
import org.citydb.operation.exporter.ExportException;
import org.citydb.operation.exporter.ExportHelper;
import org.citydb.operation.exporter.common.DatabaseExporter;
import org.citydb.sqlbuilder.literal.Placeholder;
import org.citydb.sqlbuilder.query.Select;
import org.citydb.sqlbuilder.schema.Table;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class AddressExporter extends DatabaseExporter {
    private final Table address;
    private final Select select;

    public AddressExporter(ExportHelper helper) throws SQLException {
        super(helper);
        address = tableHelper.getTable(org.citydb.database.schema.Table.ADDRESS);
        select = getBaseQuery();
        stmt = helper.getConnection().prepareStatement(Select.of(select)
                .where(address.column("id").eq(Placeholder.empty()))
                .toSql());
    }

    private Select getBaseQuery() {
        return Select.newInstance()
                .select(address.columns("id", "objectid", "identifier", "identifier_codespace", "street",
                        "house_number", "po_box", "zip_code", "city", "state", "country", "free_text", "content",
                        "content_mime_type"))
                .select(helper.getTransformOperator(address.column("multi_point")))
                .from(address);
    }

    private Select getQuery(Set<Long> ids) {
        return Select.of(select)
                .where(operationHelper.in(address.column("id"), ids));
    }

    public Address doExport(long id) throws ExportException, SQLException {
        stmt.setLong(1, id);
        try (ResultSet rs = stmt.executeQuery()) {
            return doExport(rs).get(id);
        }
    }

    public Map<Long, Address> doExport(Set<Long> ids) throws ExportException, SQLException {
        if (ids.size() == 1) {
            stmt.setLong(1, ids.iterator().next());
            try (ResultSet rs = stmt.executeQuery()) {
                return doExport(rs);
            }
        } else if (!ids.isEmpty()) {
            try (Statement stmt = helper.getConnection().createStatement();
                 ResultSet rs = stmt.executeQuery(getQuery(ids).toSql())) {
                return doExport(rs);
            }
        } else {
            return Collections.emptyMap();
        }
    }

    private Map<Long, Address> doExport(ResultSet rs) throws ExportException, SQLException {
        Map<Long, Address> addresses = new HashMap<>();
        while (rs.next()) {
            long id = rs.getLong("id");
            addresses.put(id, Address.newInstance()
                    .setObjectId(rs.getString("objectid"))
                    .setIdentifier(rs.getString("identifier"))
                    .setIdentifierCodeSpace(rs.getString("identifier_codespace"))
                    .setStreet(rs.getString("street"))
                    .setHouseNumber(rs.getString("house_number"))
                    .setPoBox(rs.getString("po_box"))
                    .setZipCode(rs.getString("zip_code"))
                    .setCity(rs.getString("city"))
                    .setState(rs.getString("state"))
                    .setCountry(rs.getString("country"))
                    .setFreeText(getArrayValue(rs.getString("free_text")))
                    .setMultiPoint(getGeometry(rs.getObject("multi_point"), MultiPoint.class))
                    .setGenericContent(rs.getString("content"))
                    .setGenericContentMimeType(rs.getString("content_mime_type"))
                    .setDescriptor(AddressDescriptor.of(id)));
        }

        return addresses;
    }
}
