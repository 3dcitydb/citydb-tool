/*
 * citydb-tool - Command-line tool for the 3D City Database
 * https://www.3dcitydb.org/
 *
 * Copyright 2022-2025
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
