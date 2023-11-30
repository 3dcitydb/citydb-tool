/*
 * citydb-tool - Command-line tool for the 3D City Database
 * https://www.3dcitydb.org/
 *
 * Copyright 2022-2023
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

package org.citydb.model.property;

import org.citydb.model.common.InlineProperty;
import org.citydb.model.common.Name;
import org.citydb.model.geometry.Geometry;

import java.util.Objects;
import java.util.Optional;

public class GeometryProperty extends Property<GeometryProperty> implements InlineProperty<Geometry<?>> {
    private final Geometry<?> geometry;
    private String lod;

    private GeometryProperty(Name name, Geometry<?> geometry) {
        super(name, DataType.GEOMETRY_PROPERTY);
        Objects.requireNonNull(geometry, "The geometry must not be null.");
        this.geometry = asChild(geometry);
    }

    public static GeometryProperty of(Name name, Geometry<?> geometry) {
        return new GeometryProperty(name, geometry);
    }

    @Override
    public Geometry<?> getObject() {
        return geometry;
    }

    public Optional<String> getLod() {
        return Optional.ofNullable(lod);
    }

    public GeometryProperty setLod(String lod) {
        this.lod = lod;
        return this;
    }

    public GeometryProperty setLod(int lod) {
        this.lod = String.valueOf(lod);
        return this;
    }

    @Override
    GeometryProperty self() {
        return this;
    }
}
