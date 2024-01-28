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

package org.citydb.model.property;

import org.citydb.model.common.Child;
import org.citydb.model.common.Describable;
import org.citydb.model.common.Name;

import java.util.Objects;
import java.util.Optional;

public abstract class Property<T extends Property<?>> extends Child implements Describable<PropertyDescriptor> {
    private final Name name;
    private Name dataType;
    private PropertyDescriptor descriptor;

    abstract T self();

    Property(Name name, Name dataType) {
        this.name = Objects.requireNonNull(name, "The property name must not be null.");
        this.dataType = dataType;
    }

    Property(Name name, DataTypeProvider provider) {
        this(name, provider.getName());
    }

    public Name getName() {
        return name;
    }

    public Optional<Name> getDataType() {
        return Optional.ofNullable(dataType);
    }

    T setDataType(Name dataType) {
        this.dataType = dataType;
        return self();
    }

    public boolean hasDataType(Name dataType) {
        return Objects.equals(this.dataType, dataType);
    }

    public boolean hasDataType(DataTypeProvider provider) {
        return provider != null && Objects.equals(dataType, provider.getName());
    }

    @Override
    public Optional<PropertyDescriptor> getDescriptor() {
        return Optional.ofNullable(descriptor);
    }

    @Override
    public T setDescriptor(PropertyDescriptor descriptor) {
        this.descriptor = descriptor;
        return self();
    }
}
