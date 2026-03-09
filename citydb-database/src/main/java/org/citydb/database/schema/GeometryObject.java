/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.database.schema;

import org.citydb.model.common.Name;

import java.util.Objects;
import java.util.Optional;

public class GeometryObject implements ValueObject {
    private final GeometryType type;
    private final Value value;
    private final Table table = Table.GEOMETRY_DATA;

    private GeometryObject(GeometryType type) {
        this.type = Objects.requireNonNull(type, "The geometry type must not be null.");
        value = new Value(new Column("geometry", type));
    }

    public static GeometryObject of(GeometryType type) {
        return new GeometryObject(type);
    }

    public GeometryType getType() {
        return type;
    }

    @Override
    public Name getName() {
        return type.getName();
    }

    public Table getTable() {
        return table;
    }

    @Override
    public Optional<Value> getValue() {
        return Optional.of(value);
    }

    @Override
    public String toString() {
        return type.getName().toString();
    }
}
