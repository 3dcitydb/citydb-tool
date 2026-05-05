/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.database.schema;

import org.citydb.model.common.Name;
import org.citydb.model.common.PrefixedName;

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

    Property(Name name, String description, Integer parentIndex, Value value, String typeIdentifier,
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
            type = schemaMapping.getDataType(PrefixedName.of(typeIdentifier));
            if (type == DataType.UNDEFINED) {
                throw new SchemaException("The property references an undefined type " + typeIdentifier + ".");
            }
        }

        if (targetIdentifier != null) {
            FeatureType candidate = schemaMapping.getFeatureType(PrefixedName.of(targetIdentifier));
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
