/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
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

    public abstract boolean removeFromParent();

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
