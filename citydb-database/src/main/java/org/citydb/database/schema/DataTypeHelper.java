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

package org.citydb.database.schema;

import org.citydb.database.adapter.DatabaseAdapter;
import org.citydb.model.common.Name;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

public class DataTypeHelper {
    private final Map<Integer, Name> dataTypesById = new HashMap<>();
    private final Map<Name, Integer> dataTypesByName = new HashMap<>();

    private DataTypeHelper() {
    }

    public static DataTypeHelper newInstance(DatabaseAdapter adapter) throws SQLException {
        DataTypeHelper helper = new DataTypeHelper();
        helper.initialize(adapter);
        return helper;
    }

    private void initialize(DatabaseAdapter adapter) throws SQLException {
        try (Connection connection = adapter.getPool().getConnection();
             Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("select d.id, d.typename, n.namespace from " +
                     adapter.getConnectionDetails().getSchema() + "." + Table.DATATYPE + " d, " +
                     adapter.getConnectionDetails().getSchema() + "." + Table.NAMESPACE + " n " +
                     "where d.namespace_id = n.id")) {
            while (rs.next()) {
                dataTypesById.put(rs.getInt("id"), Name.of(rs.getString("typename"), rs.getString("namespace")));
            }

            dataTypesById.forEach((key, value) -> dataTypesByName.put(value, key));
        }
    }

    public Integer getDataTypeId(Name name) {
        return dataTypesByName.get(name);
    }

    public Integer getDataTypeId(String localName, String namespace) {
        return getDataTypeId(Name.of(localName, namespace));
    }

    public Name getDataType(int id) {
        return dataTypesById.get(id);
    }
}
