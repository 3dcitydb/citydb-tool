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
import org.citydb.model.common.Name;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public class Property implements ValueObject, Joinable {
    private final Name name;
    private final Integer parentIndex;
    private final Value value;
    private final String typeIdentifier;
    private final String targetIdentifier;
    private final JoinTable joinTable;
    private DataType type;
    private FeatureType targetFeature;
    private GeometryType targetGeometry;
    private Map<Name, Property> properties;
    private Join join;

    private Property(Name name, Integer parentIndex, Value value, String typeIdentifier, String targetIdentifier,
                     Join join, JoinTable joinTable) {
        this.name = name;
        this.parentIndex = parentIndex;
        this.value = value;
        this.typeIdentifier = typeIdentifier;
        this.targetIdentifier = targetIdentifier;
        this.join = join;
        this.joinTable = joinTable;
    }

    static Property of(JSONObject object) throws SchemaException {
        String propertyName = object.getString("name");
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
        } else if ("core:FeatureProperty".equals(typeIdentifier) && targetIdentifier == null) {
            throw new SchemaException("A feature property must define a target feature.");
        } else if ("core:GeometryProperty".equals(typeIdentifier) && targetIdentifier == null) {
            throw new SchemaException("A geometry property must define a target geometry.");
        } else if (joinObject != null && joinTableObject != null) {
            throw new SchemaException("A property must not define both a join and a join table.");
        }

        if (typeIdentifier != null && targetIdentifier == null) {
            targetIdentifier = switch (typeIdentifier) {
                case "core:AddressProperty" -> "core:Address";
                case "core:AppearanceProperty" -> "app:Appearance";
                case "core:ImplicitGeometryProperty" -> "core:ImplicitGeometry";
                default -> null;
            };
        }

        return new Property(Name.of(propertyName, namespace), parentIndex,
                valueObject != null ? Value.of(valueObject) : null,
                typeIdentifier, targetIdentifier,
                joinObject != null ? Join.of(joinObject) : null,
                joinTableObject != null ? JoinTable.of(joinTableObject) : null);
    }

    @Override
    public Name getName() {
        return name;
    }

    Integer getParentIndex() {
        return parentIndex;
    }

    @Override
    public Optional<Value> getValue() {
        return Optional.ofNullable(value);
    }

    public Optional<DataType> getType() {
        return Optional.ofNullable(type);
    }

    public Optional<FeatureType> getTargetFeature() {
        return Optional.ofNullable(targetFeature);
    }

    public Optional<GeometryType> getTargetGeometry() {
        return Optional.ofNullable(targetGeometry);
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
            targetFeature = schemaMapping.getFeatureTypeByIdentifier(targetIdentifier);
            if (targetFeature == FeatureType.UNDEFINED) {
                targetGeometry = GeometryType.of(targetIdentifier);
                if (targetGeometry == null) {
                    throw new SchemaException("The property references an undefined target " +
                            targetIdentifier + ".");
                }
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
