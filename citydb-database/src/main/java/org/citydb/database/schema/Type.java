/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.database.schema;

import org.citydb.model.common.Name;

import java.util.*;

public abstract class Type<T extends Type<T>> implements Joinable {
    final int id;
    final Name name;
    final Table table;
    final String description;
    final boolean isAbstract;
    final Integer superTypeId;
    final Map<Name, Property> properties;
    final Join join;
    final JoinTable joinTable;
    T superType;

    Type(int id, Name name, Table table, String description, boolean isAbstract, Integer superTypeId,
         Map<Name, Property> properties, Join join, JoinTable joinTable) {
        this.id = id;
        this.name = name;
        this.table = table;
        this.description = description;
        this.isAbstract = isAbstract;
        this.superTypeId = superTypeId;
        this.properties = properties;
        this.join = join;
        this.joinTable = joinTable;
    }

    abstract T self();

    public int getId() {
        return id;
    }

    @Override
    public Name getName() {
        return name;
    }

    public Table getTable() {
        return table;
    }

    public Optional<String> getDescription() {
        return Optional.ofNullable(description);
    }

    public boolean isAbstract() {
        return isAbstract;
    }

    public Optional<T> getSuperType() {
        return Optional.ofNullable(superType);
    }

    public Map<Name, Property> getDeclaredProperties() {
        return properties != null ? properties : Collections.emptyMap();
    }

    public Map<Name, Property> getProperties() {
        Map<Name, Property> properties = new LinkedHashMap<>();
        if (superType != null) {
            properties.putAll(superType.getProperties());
        }

        properties.putAll(getDeclaredProperties());
        return properties;
    }

    @Override
    public Optional<Join> getJoin() {
        return Optional.ofNullable(join);
    }

    @Override
    public Optional<JoinTable> getJoinTable() {
        return Optional.ofNullable(joinTable);
    }

    public List<T> getTypeHierarchy() {
        T superType = self();
        List<T> hierarchy = new ArrayList<>();
        do {
            hierarchy.add(superType);
        } while ((superType = superType.getSuperType().orElse(null)) != null);

        return hierarchy;
    }

    public boolean isSameOrSubTypeOf(T type) {
        return this == type || isSubTypeOf(type);
    }

    public boolean isSubTypeOf(T type) {
        T superType = self();
        while ((superType = superType.getSuperType().orElse(null)) != null) {
            if (superType == type) {
                return true;
            }
        }

        return false;
    }

    void postprocess(SchemaMapping schemaMapping) throws SchemaException {
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
