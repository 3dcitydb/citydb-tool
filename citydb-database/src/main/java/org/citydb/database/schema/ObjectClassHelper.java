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

public class ObjectClassHelper {
    private final Map<Integer, ObjectClass> objectClassesById = new HashMap<>();
    private final Map<Name, ObjectClass> objectClassesByName = new HashMap<>();

    private ObjectClassHelper() {
    }

    public static ObjectClassHelper newInstance(DatabaseAdapter adapter) throws SQLException {
        ObjectClassHelper helper = new ObjectClassHelper();
        helper.initialize(adapter);
        return helper;
    }

    private void initialize(DatabaseAdapter adapter) throws SQLException {
        try (Connection connection = adapter.getPool().getConnection();
             Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("select o.id, o.superclass_id, o.classname, o.is_abstract, " +
                     "o.is_toplevel, n.namespace from " +
                     adapter.getConnectionDetails().getSchema() + "." + Table.OBJECTCLASS + " o, " +
                     adapter.getConnectionDetails().getSchema() + "." + Table.NAMESPACE + " n " +
                     "where o.namespace_id = n.id")) {
            while (rs.next()) {
                int id = rs.getInt("id");
                ObjectClass objectClass = id != 0 ?
                        new ObjectClass(
                                id,
                                Name.of(rs.getString("classname"), rs.getString("namespace")),
                                rs.getInt("superclass_id"),
                                rs.getInt("is_abstract") != 0,
                                rs.getInt("is_toplevel") != 0) :
                        ObjectClass.UNDEFINED;

                objectClassesById.put(objectClass.getId(), objectClass);
                objectClassesByName.put(objectClass.getName(), objectClass);
            }
        }
    }

    public ObjectClass getObjectClass(Name name) {
        return objectClassesByName.getOrDefault(name, ObjectClass.UNDEFINED);
    }

    public ObjectClass getObjectClass(String localName, String namespace) {
        return getObjectClass(Name.of(localName, namespace));
    }

    public ObjectClass getObjectClass(int id) {
        return objectClassesById.getOrDefault(id, ObjectClass.UNDEFINED);
    }
}
