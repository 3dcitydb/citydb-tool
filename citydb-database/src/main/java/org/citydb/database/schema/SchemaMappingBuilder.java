/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.database.schema;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.citydb.database.adapter.DatabaseAdapter;
import org.citydb.model.common.Name;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class SchemaMappingBuilder {

    private SchemaMappingBuilder() {
    }

    public static SchemaMappingBuilder newInstance() {
        return new SchemaMappingBuilder();
    }

    public SchemaMapping build(DatabaseAdapter adapter) throws SchemaException {
        try (Connection connection = adapter.getPool().getConnection()) {
            SchemaMapping schemaMapping = new SchemaMapping();
            buildNamespaces(schemaMapping, connection, adapter);
            buildDataTypes(schemaMapping, connection, adapter);
            buildFeatureTypes(schemaMapping, connection, adapter);
            return schemaMapping.build();
        } catch (SQLException e) {
            throw new SchemaException("Failed to query schema mapping.", e);
        }
    }

    private void buildNamespaces(SchemaMapping schemaMapping, Connection connection, DatabaseAdapter adapter) throws SchemaException, SQLException {
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("select id, namespace, alias " +
                     "from " + adapter.getConnectionDetails().getSchema() + "." + Table.NAMESPACE)) {
            while (rs.next()) {
                schemaMapping.addNamespace(Namespace.of(rs.getInt("id"),
                        rs.getString("namespace"),
                        rs.getString("alias")));
            }
        }
    }

    private void buildDataTypes(SchemaMapping schemaMapping, Connection connection, DatabaseAdapter adapter) throws SchemaException, SQLException {
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("select id, supertype_id, typename, is_abstract, namespace_id, schema " +
                     "from " + adapter.getConnectionDetails().getSchema() + "." + Table.DATATYPE)) {
            while (rs.next()) {
                int id = rs.getInt("id");
                if (id != 1) {
                    Integer superTypeId = rs.getInt("supertype_id");
                    if (rs.wasNull()) {
                        superTypeId = null;
                    }

                    String typeName = rs.getString("typename");
                    if (typeName == null) {
                        throw new SchemaException("No name defined for data type (ID " + id + ").");
                    }

                    Namespace namespace = schemaMapping.getNamespace(rs.getInt("namespace_id"));
                    if (namespace == Namespace.UNDEFINED) {
                        throw new SchemaException("No namespace defined for data type (ID " + id + ").");
                    }

                    String schema = rs.getString("schema");
                    if (schema != null) {
                        JSONObject object = JSON.parseObject(schema);
                        if (object != null) {
                            schemaMapping.addDataType(DataType.of(id,
                                    Name.of(typeName, namespace.getURI()),
                                    rs.getInt("is_abstract") != 0,
                                    superTypeId,
                                    object,
                                    adapter));
                        } else {
                            throw new SchemaException("Failed to parse JSON schema of data type (ID: " + id + ").");
                        }
                    }
                } else {
                    schemaMapping.addDataType(DataType.UNDEFINED);
                }
            }
        }
    }

    private void buildFeatureTypes(SchemaMapping schemaMapping, Connection connection, DatabaseAdapter adapter) throws SchemaException, SQLException {
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("select id, superclass_id, classname, is_abstract, is_toplevel, " +
                     "namespace_id, schema " +
                     "from " + adapter.getConnectionDetails().getSchema() + "." + Table.OBJECTCLASS)) {
            while (rs.next()) {
                int id = rs.getInt("id");
                if (id != 1) {
                    Integer superTypeId = rs.getInt("superclass_id");
                    if (rs.wasNull()) {
                        superTypeId = null;
                    }

                    String className = rs.getString("classname");
                    if (className == null) {
                        throw new SchemaException("No name defined for feature type (ID " + id + ").");
                    }

                    Namespace namespace = schemaMapping.getNamespace(rs.getInt("namespace_id"));
                    if (namespace == Namespace.UNDEFINED) {
                        throw new SchemaException("No namespace defined for feature type (ID " + id + ").");
                    }

                    String schema = rs.getString("schema");
                    if (schema != null) {
                        JSONObject object = JSON.parseObject(schema);
                        if (object != null) {
                            schemaMapping.addFeatureType(FeatureType.of(id,
                                    Name.of(className, namespace.getURI()),
                                    rs.getInt("is_abstract") != 0,
                                    rs.getInt("is_toplevel") != 0,
                                    superTypeId,
                                    object,
                                    adapter));
                        } else {
                            throw new SchemaException("Failed to parse JSON schema of feature type (ID: " + id + ").");
                        }
                    }
                } else {
                    schemaMapping.addFeatureType(FeatureType.UNDEFINED);
                }
            }
        }
    }
}
