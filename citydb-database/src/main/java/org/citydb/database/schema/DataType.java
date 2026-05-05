/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.database.schema;

import org.citydb.model.common.Name;
import org.citydb.model.common.Namespaces;

import java.util.Map;
import java.util.Optional;

public class DataType extends Type<DataType> implements ValueObject {
    public static final DataType UNDEFINED = new DataType(1, "core:Undefined", Name.of("Undefined", Namespaces.CORE),
            Table.PROPERTY, null, false, null, null, null, null, null);

    private final Value value;

    DataType(int id, String identifier, Name name, Table table, String description, boolean isAbstract,
             Integer superTypeId, Map<Name, Property> properties, Value value, Join join, JoinTable joinTable) {
        super(id, identifier, name, table, description, isAbstract, superTypeId, properties, join, joinTable);
        this.value = value;
    }

    @Override
    public Optional<Value> getValue() {
        return Optional.ofNullable(value);
    }

    @Override
    void postprocess(SchemaMapping schemaMapping) throws SchemaException {
        super.postprocess(schemaMapping);

        if (superTypeId != null) {
            superType = schemaMapping.getDataType(superTypeId);
            if (superType == DataType.UNDEFINED) {
                throw new SchemaException("The data type (ID " + id + ") references an undefined " +
                        "supertype (ID " + superTypeId + ").");
            }
        }

        try {
            if (properties != null) {
                if (value != null) {
                    value.postprocess(properties);
                }

                for (Property property : properties.values()) {
                    property.postprocess(properties, schemaMapping);
                }

                properties.values().removeIf(property -> property.getParentIndex() != null);
            }
        } catch (SchemaException e) {
            throw new SchemaException("Failed to build data type (ID " + id + ").", e);
        }
    }

    @Override
    DataType self() {
        return this;
    }
}
