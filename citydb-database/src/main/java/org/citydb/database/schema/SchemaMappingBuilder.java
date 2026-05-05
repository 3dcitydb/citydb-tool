/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.database.schema;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.citydb.core.version.Version;
import org.citydb.database.adapter.DatabaseAdapter;
import org.citydb.model.common.Name;
import org.citydb.model.common.Namespaces;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;

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
                int id = rs.getInt("id");

                String namespace = rs.getString("namespace");
                if (rs.wasNull()) {
                    throw new SchemaException("No namespace URI defined for the namespace (ID " + id + ").");
                }

                schemaMapping.addNamespace(new Namespace(id, namespace, rs.getString("alias")));
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
                            schemaMapping.addDataType(buildDataType(id,
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
                            schemaMapping.addFeatureType(buildFeatureType(id,
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

    private DataType buildDataType(int id, Name name, boolean isAbstract, Integer superTypeId, JSONObject object,
                                   DatabaseAdapter adapter) throws SchemaException {
        String identifier = object.getString("identifier");
        String tableName = object.getString("table");
        String description = object.getString("description");
        JSONArray propertiesArray = object.getJSONArray("properties");
        JSONObject valueObject = object.getJSONObject("value");
        JSONObject joinObject = object.getJSONObject("join");
        JSONObject joinTableObject = object.getJSONObject("joinTable");

        if (identifier == null) {
            throw new SchemaException("No identifier defined for data type (ID " + id + ").");
        } else if (tableName == null) {
            throw new SchemaException("No table defined for data type (ID " + id + ").");
        } else if (joinObject != null && joinTableObject != null) {
            throw new SchemaException("The data type (ID " + id + ") defines both a join and a join table.");
        }

        Table table = Table.of(tableName);
        if (table == null) {
            throw new SchemaException("The table " + tableName + " of data type (ID: " + id + ") is not supported.");
        }

        try {
            return new DataType(id, identifier, name, table, description, isAbstract, superTypeId,
                    propertiesArray != null ? buildProperties(propertiesArray, adapter) : null,
                    valueObject != null ? buildValue(valueObject) : null,
                    joinObject != null ? buildJoin(joinObject) : null,
                    joinTableObject != null ? buildJoinTable(joinTableObject) : null);
        } catch (SchemaException e) {
            throw new SchemaException("Failed to build data type (ID: " + id + ").", e);
        }
    }

    private FeatureType buildFeatureType(int id, Name name, boolean isAbstract, boolean isTopLevel, Integer superTypeId,
                                         JSONObject object, DatabaseAdapter adapter) throws SchemaException {
        String identifier = object.getString("identifier");
        String tableName = object.getString("table");
        String description = object.getString("description");
        JSONArray propertiesArray = object.getJSONArray("properties");
        JSONObject joinObject = object.getJSONObject("join");
        JSONObject joinTableObject = object.getJSONObject("joinTable");

        if (identifier == null) {
            throw new SchemaException("No identifier defined for feature type (ID " + id + ").");
        } else if (tableName == null) {
            throw new SchemaException("No table defined for feature type (ID " + id + ").");
        } else if (joinObject != null && joinTableObject != null) {
            throw new SchemaException("The feature type (ID " + id + ") defines both a join and a join table.");
        }

        Table table = Table.of(tableName);
        if (table == null) {
            throw new SchemaException("The table " + tableName + " of feature type (ID: " + id + ") is not supported.");
        }

        try {
            return new FeatureType(id, identifier, name, table, description, isAbstract, isTopLevel, superTypeId,
                    propertiesArray != null ? buildProperties(propertiesArray, adapter) : null,
                    joinObject != null ? buildJoin(joinObject) : null,
                    joinTableObject != null ? buildJoinTable(joinTableObject) : null);
        } catch (SchemaException e) {
            throw new SchemaException("Failed to build feature type (ID: " + id + ").", e);
        }
    }

    private Map<Name, Property> buildProperties(JSONArray propertiesArray, DatabaseAdapter adapter) throws SchemaException {
        Map<Name, Property> properties = null;
        if (!propertiesArray.isEmpty()) {
            properties = new LinkedHashMap<>();
            for (Object item : propertiesArray) {
                if (item instanceof JSONObject propertyObject) {
                    Property property = buildProperty(propertyObject, adapter);
                    properties.put(property.getName(), property);
                }
            }

            if (properties.size() != propertiesArray.size()) {
                throw new SchemaException("The properties array contains invalid properties.");
            }
        }

        return properties;
    }

    private Property buildProperty(JSONObject object, DatabaseAdapter adapter) throws SchemaException {
        String propertyName = object.getString("name");
        String description = object.getString("description");
        String namespace = object.getString("namespace");
        Integer parentIndex = object.getInteger("parent");
        JSONObject valueObject = object.getJSONObject("value");
        String typeIdentifier = object.getString("type");
        String targetIdentifier = object.getString("target");
        String relationTypeName = object.getString("relationType");
        JSONObject joinObject = object.getJSONObject("join");
        JSONObject joinTableObject = object.getJSONObject("joinTable");

        if (propertyName == null) {
            throw new SchemaException("No name defined for the property.");
        } else if (namespace == null) {
            throw new SchemaException("No namespace defined for the property.");
        } else if (valueObject != null && typeIdentifier != null) {
            throw new SchemaException("A property must not define both a value and a type.");
        } else if (joinObject != null && joinTableObject != null) {
            throw new SchemaException("A property must not define both a join and a join table.");
        }

        if (typeIdentifier != null && targetIdentifier == null) {
            switch (typeIdentifier) {
                case "core:FeatureProperty" ->
                        throw new SchemaException("A feature property must define a target feature.");
                case "core:GeometryProperty" ->
                        throw new SchemaException("A geometry property must define a target geometry.");
                case "core:AddressProperty" -> targetIdentifier = "core:Address";
                case "core:AppearanceProperty" -> targetIdentifier = "app:Appearance";
                case "core:ImplicitGeometryProperty" -> targetIdentifier = "core:ImplicitGeometry";
            }
        }

        RelationType relationType = "core:FeatureProperty".equals(typeIdentifier) ?
                getRelationType(propertyName, namespace, relationTypeName, adapter) :
                null;

        return new Property(Name.of(propertyName, namespace), description, parentIndex,
                valueObject != null ? buildValue(valueObject) : null,
                typeIdentifier, targetIdentifier, relationType,
                joinObject != null ? buildJoin(joinObject) : null,
                joinTableObject != null ? buildJoinTable(joinTableObject) : null);
    }

    private Value buildValue(JSONObject object) throws SchemaException {
        String columnName = object.getString("column");
        String typeName = object.getString("type");
        Integer propertyIndex = object.getInteger("property");

        if (columnName == null && typeName == null && propertyIndex == null) {
            throw new SchemaException("A value must define either a column and type or a property index.");
        } else if (propertyIndex == null) {
            if (columnName == null || typeName == null) {
                throw new SchemaException("A value must define both a column and type.");
            }

            ColumnType type = ColumnType.of(typeName);
            if (type == null) {
                throw new SchemaException("The value uses an unsupported column data type " + typeName + ".");
            }

            return new Value(new Column(columnName, type));
        } else {
            return new Value(propertyIndex);
        }
    }

    private Join buildJoin(JSONObject object) throws SchemaException {
        String tableName = object.getString("table");
        String fromColumn = object.getString("fromColumn");
        String toColumn = object.getString("toColumn");
        JSONArray conditionsArray = object.getJSONArray("conditions");

        if (tableName == null) {
            throw new SchemaException("No table defined for the join.");
        } else if (fromColumn == null) {
            throw new SchemaException("No from-column defined for the join.");
        } else if (toColumn == null) {
            throw new SchemaException("No to-column defined for the join.");
        }

        Table table = Table.of(tableName);
        if (table == null) {
            throw new SchemaException("The join target " + tableName + " is not supported.");
        }

        Map<String, Condition> conditions = null;
        if (conditionsArray != null && !conditionsArray.isEmpty()) {
            conditions = new LinkedHashMap<>();
            for (Object item : conditionsArray) {
                if (item instanceof JSONObject conditionObject) {
                    Condition condition = buildCondition(conditionObject);
                    conditions.put(condition.getColumn().getName(), condition);
                }
            }

            if (conditions.size() != conditionsArray.size()) {
                throw new SchemaException("The conditions array contains invalid conditions.");
            }
        }

        return new Join(table, fromColumn, toColumn, conditions);
    }

    private JoinTable buildJoinTable(JSONObject object) throws SchemaException {
        String tableName = object.getString("table");
        JSONObject sourceJoin = object.getJSONObject("sourceJoin");
        JSONObject targetJoin = object.getJSONObject("targetJoin");

        if (tableName == null) {
            throw new SchemaException("No table defined for the join table.");
        } else if (sourceJoin == null) {
            throw new SchemaException("No source join defined for the join table.");
        } else if (targetJoin == null) {
            throw new SchemaException("No target join defined for the join table.");
        }

        Table table = Table.of(tableName);
        if (table == null) {
            throw new SchemaException("The join table " + tableName + " is not supported.");
        }

        return new JoinTable(table, buildJoin(sourceJoin), buildJoin(targetJoin));
    }

    private Condition buildCondition(JSONObject object) throws SchemaException {
        String columnName = object.getString("column");
        String value = object.getString("value");
        String typeName = object.getString("type");

        if (columnName == null) {
            throw new SchemaException("No column defined for the join condition.");
        } else if (value == null) {
            throw new SchemaException("No value defined for the join condition.");
        } else if (typeName == null) {
            throw new SchemaException("No column data type defined for the join condition.");
        }

        SimpleType type = SimpleType.of(typeName);
        if (!SimpleType.JOIN_CONDITION_TYPES.contains(type)) {
            throw new SchemaException("The join condition uses an unsupported data type " + typeName + ".");
        }

        return new Condition(new Column(columnName, type), value);
    }

    private RelationType getRelationType(String name, String namespace, String relationTypeName, DatabaseAdapter adapter) throws SchemaException {
        if (adapter.getDatabaseMetadata().getVersion().compareTo(Version.of(5, 1, 0)) < 0
                && relationTypeName == null
                && Namespaces.isCityDBNamespace(namespace)) {
            return switch (namespace) {
                case Namespaces.CORE -> switch (name) {
                    case "generalizesTo", "relatedTo" -> RelationType.RELATES;
                    default -> RelationType.CONTAINS;
                };
                case Namespaces.DYNAMIZER -> name.equals("sensorLocation") ?
                        RelationType.RELATES :
                        RelationType.CONTAINS;
                case Namespaces.TRANSPORTATION -> switch (name) {
                    case "predecessor", "successor" -> RelationType.RELATES;
                    default -> RelationType.CONTAINS;
                };
                case Namespaces.CITY_OBJECT_GROUP -> switch (name) {
                    case "parent", "groupMember" -> RelationType.RELATES;
                    default -> RelationType.CONTAINS;
                };
                case Namespaces.VERSIONING -> switch (name) {
                    case "versionMember", "oldFeature", "newFeature" -> RelationType.RELATES;
                    default -> RelationType.CONTAINS;
                };
                default -> RelationType.CONTAINS;
            };
        } else {
            RelationType relationType = RelationType.of(relationTypeName);
            if (relationTypeName == null) {
                throw new SchemaException("A feature property must define a relation type.");
            } else if (relationType == null) {
                throw new SchemaException("The relation type " + relationTypeName + " is unsupported.");
            }

            return relationType;
        }
    }
}
