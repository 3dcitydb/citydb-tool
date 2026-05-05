/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.database.schema;

import org.citydb.model.common.Name;
import org.citydb.model.common.Namespaces;

import java.util.Map;

public class FeatureType extends Type<FeatureType> {
    public static final FeatureType UNDEFINED = new FeatureType(1, Name.of("Undefined", Namespaces.CORE), Table.FEATURE,
            null, false, false, null, null, null, null);

    private final boolean isTopLevel;

    FeatureType(int id, Name name, Table table, String description, boolean isAbstract, boolean isTopLevel,
                Integer superTypeId, Map<Name, Property> properties, Join join, JoinTable joinTable) {
        super(id, name, table, description, isAbstract, superTypeId, properties, join, joinTable);
        this.isTopLevel = isTopLevel;
    }

    public boolean isTopLevel() {
        return isTopLevel;
    }

    @Override
    void postprocess(SchemaMapping schemaMapping) throws SchemaException {
        super.postprocess(schemaMapping);

        if (superTypeId != null) {
            superType = schemaMapping.getFeatureType(superTypeId);
            if (superType == FeatureType.UNDEFINED) {
                throw new SchemaException("The feature type (ID " + id + ") references an undefined " +
                        "supertype (ID " + superTypeId + ").");
            }
        }

        try {
            if (properties != null) {
                for (Property property : properties.values()) {
                    property.postprocess(properties, schemaMapping);
                    if (property.getJoin().isEmpty() && property.getJoinTable().isEmpty()) {
                        Table table = property.getType().map(DataType::getTable).orElse(null);
                        if (table == Table.PROPERTY) {
                            property.setJoin(new Join(Table.PROPERTY, "id", "feature_id")
                                    .postprocess(property, schemaMapping));
                        }
                    }

                    Join join = property.getJoin().orElse(null);
                    if (join != null
                            && join.getTable() == Table.PROPERTY
                            && !join.hasConditionOn("parent_id")
                            && hasDuplicateChildProperty(property)) {
                        join.addCondition(new Condition(new Column("parent_id", SimpleType.INTEGER), "null"));
                    }
                }

                properties.values().removeIf(property -> property.getParentIndex() != null);
            }
        } catch (SchemaException e) {
            throw new SchemaException("Failed to build feature type (ID " + id + ").", e);
        }
    }

    private boolean hasDuplicateChildProperty(Property property) {
        Property duplicate = property.getProperties().get(property.getName());
        if (duplicate == null) {
            duplicate = property.getType()
                    .map(type -> type.getProperties().get(property.getName()))
                    .orElse(null);
        }

        return duplicate != null && duplicate.getJoin().isPresent();
    }

    @Override
    FeatureType self() {
        return this;
    }
}
