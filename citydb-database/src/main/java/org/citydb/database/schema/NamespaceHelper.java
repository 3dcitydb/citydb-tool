/*
 * citydb-tool - Command-line tool for the 3D City Database
 * https://www.3dcitydb.org/
 *
 * Copyright 2022-2023
 * Virtual City Systems, Germany
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
import org.citydb.model.common.Namespaces;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

public class NamespaceHelper {
    private final Map<Integer, String> namespacesById = new HashMap<>();
    private final Map<String, Integer> namespacesByName = new HashMap<>();
    private final Map<String, String> aliasesByName = new HashMap<>();

    private NamespaceHelper() {
    }

    public static NamespaceHelper newInstance(DatabaseAdapter adapter) throws SQLException {
        NamespaceHelper helper = new NamespaceHelper();
        helper.initialize(adapter);
        return helper;
    }

    private void initialize(DatabaseAdapter adapter) throws SQLException {
        try (Connection connection = adapter.getPool().getConnection();
             Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("select id, namespace, alias from " +
                     adapter.getConnectionDetails().getSchema() + "." + Table.NAMESPACE)) {
            while (rs.next()) {
                String namespace = Namespaces.ensureNonNull(rs.getString("namespace"));
                namespacesById.put(rs.getInt("id"), namespace);
                aliasesByName.put(namespace, rs.getString("alias"));
            }

            namespacesById.forEach((key, value) -> namespacesByName.put(value, key));
        }
    }

    public Integer getNamespaceId(String namespace) {
        return namespacesByName.get(Namespaces.ensureNonNull(namespace));
    }

    public String getNamespace(int id) {
        return namespacesById.get(id);
    }

    public String getAlias(String namespace) {
        return aliasesByName.get(Namespaces.ensureNonNull(namespace));
    }
}
