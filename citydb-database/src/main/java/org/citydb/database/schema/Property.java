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

package org.citydb.database.schema;

import com.alibaba.fastjson2.JSONObject;
import org.citydb.core.version.Version;
import org.citydb.database.adapter.DatabaseAdapter;
import org.citydb.model.common.Name;
import org.citydb.model.common.Namespaces;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public class Property implements ValueObject, Typeable, Joinable {
    private final Name name;
    private final String description;
    private final Integer parentIndex;
    private final Value value;
    private final String typeIdentifier;
    private final String targetIdentifier;
    private final RelationType relationType;
    private final JoinTable joinTable;
    private DataType type;
    private FeatureType targetFeature;
    private GeometryType targetGeometry;
    private Map<Name, Property> properties;
    private Join join;

    private Property(Name name, String description, Integer parentIndex, Value value, String typeIdentifier,
                     String targetIdentifier, RelationType relationType, Join join, JoinTable joinTable) {
        this.name = name;
        this.description = description;
        this.parentIndex = parentIndex;
        this.value = value;
        this.typeIdentifier = typeIdentifier;
        this.targetIdentifier = targetIdentifier;
        this.relationType = relationType;
        this.join = join;
        this.joinTable = joinTable;
    }

    static Property of(JSONObject object, DatabaseAdapter adapter) throws SchemaException {
        String propertyName = object.getString("name");
        String description = object.getString("description");
        String namespace = object.getString("namespace");
        Integer parentIndex = object.getInteger("parent");
        JSONObject valueObject = object.getJSONObject("value");
        String typeIdentifier = object.getString("type");
        String targetIdentifier = object.getString("target");
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
                getRelationType(propertyName, namespace, object, adapter) :
                null;

        return new Property(Name.of(propertyName, namespace), description, parentIndex,
                valueObject != null ? Value.of(valueObject) : null,
                typeIdentifier, targetIdentifier, relationType,
                joinObject != null ? Join.of(joinObject) : null,
                joinTableObject != null ? JoinTable.of(joinTableObject) : null);
    }

    @Override
    public Name getName() {
        return name;
    }

    public Optional<String> getDescription() {
        return Optional.ofNullable(description);
    }

    Integer getParentIndex() {
        return parentIndex;
    }

    @Override
    public Optional<Value> getValue() {
        return Optional.ofNullable(value);
    }

    @Override
    public Optional<DataType> getType() {
        return Optional.ofNullable(type);
    }

    public Optional<FeatureType> getTargetFeature() {
        return Optional.ofNullable(targetFeature);
    }

    public Optional<GeometryType> getTargetGeometry() {
        return Optional.ofNullable(targetGeometry);
    }

    public Optional<RelationType> getRelationType() {
        return Optional.ofNullable(relationType);
    }

    public Map<Name, Property> getProperties() {
        return properties != null ? properties : Collections.emptyMap();
    }

    private void addProperty(Property property) {
        if (properties == null) {
            properties = new LinkedHashMap<>();
        }

        properties.put(property.name, property);
    }

    @Override
    public Optional<Join> getJoin() {
        return Optional.ofNullable(join);
    }

    Property setJoin(Join join) {
        this.join = join;
        return this;
    }

    @Override
    public Optional<JoinTable> getJoinTable() {
        return Optional.ofNullable(joinTable);
    }

    private static RelationType getRelationType(String name, String namespace, JSONObject object, DatabaseAdapter adapter) throws SchemaException {
        if (adapter.getDatabaseMetadata().getVersion().compareTo(Version.of(5, 1, 0)) < 0) {
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
            String relationTypeName = object.getString("relationType");
            RelationType relationType = RelationType.of(relationTypeName);

            if (relationTypeName == null) {
                throw new SchemaException("A feature property must define a relation type.");
            } else if (relationType == null) {
                throw new SchemaException("The relation type " + relationTypeName + " is unsupported.");
            }

            return relationType;
        }
    }

    void postprocess(Map<Name, Property> properties, SchemaMapping schemaMapping) throws SchemaException {
        if (parentIndex != null) {
            if (parentIndex < 0 || parentIndex >= properties.size()) {
                throw new SchemaException("The parent index of the property is out of bounds.");
            }

            properties.values().stream()
                    .skip(parentIndex)
                    .findFirst().ifPresent(property -> property.addProperty(this));
        }

        if (value != null) {
            value.postprocess(properties);
        } else if (typeIdentifier != null) {
            type = schemaMapping.getDataTypeByIdentifier(typeIdentifier);
            if (type == DataType.UNDEFINED) {
                throw new SchemaException("The property references an undefined type " + typeIdentifier + ".");
            }
        }

        if (targetIdentifier != null) {
            FeatureType candidate = schemaMapping.getFeatureTypeByIdentifier(targetIdentifier);
            if (candidate != FeatureType.UNDEFINED) {
                targetFeature = candidate;
            } else {
                targetGeometry = GeometryType.of(targetIdentifier);
            }

            if (targetFeature == null && targetGeometry == null) {
                throw new SchemaException("The property references an undefined target " + targetIdentifier + ".");
            }
        }

        if (join != null) {
            join.postprocess(this, schemaMapping);
        } else if (joinTable != null) {
            joinTable.postprocess(this, schemaMapping);
        }
    }

    @Override
    public String toString() {
        return name.toString();
    }
}
