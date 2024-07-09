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

import org.citydb.model.address.Address;
import org.citydb.model.common.Child;
import org.citydb.model.common.InlineOrByReferenceProperty;
import org.citydb.model.common.Name;
import org.citydb.model.common.Reference;
import org.citydb.model.feature.Feature;

import java.util.Objects;
import java.util.Optional;

public class AddressProperty extends Property<AddressProperty> implements InlineOrByReferenceProperty<Address> {
    private Address address;
    private Reference reference;

    private AddressProperty(Name name, Address address) {
        super(name, DataType.ADDRESS_PROPERTY);
        Objects.requireNonNull(address, "The address must not be null.");
        this.address = asChild(address);
        reference = null;
    }

    private AddressProperty(Name name, Reference reference) {
        super(name, DataType.ADDRESS_PROPERTY);
        Objects.requireNonNull(reference, "The reference must not be null.");
        this.reference = asChild(reference);
        address = null;
    }

    public static AddressProperty of(Name name, Address address) {
        return new AddressProperty(name, address);
    }

    public static AddressProperty of(Name name, Reference reference) {
        return new AddressProperty(name, reference);
    }

    @Override
    public Optional<Address> getObject() {
        return Optional.ofNullable(address);
    }

    @Override
    public AddressProperty setObject(Address address) {
        if (address != null) {
            this.address = asChild(address);
            reference = null;
        }

        return this;
    }

    @Override
    public Optional<Reference> getReference() {
        return Optional.ofNullable(reference);
    }

    @Override
    public AddressProperty setReference(Reference reference) {
        if (reference != null) {
            this.reference = asChild(reference);
            address = null;
        }

        return this;
    }

    @Override
    public boolean removeFromParent() {
        Child parent = getParent().orElse(null);
        if (parent instanceof Feature feature) {
            return feature.getAddresses().remove(this);
        } else if (parent instanceof Attribute attribute) {
            return attribute.getProperties().remove(this);
        } else {
            return false;
        }
    }

    @Override
    AddressProperty self() {
        return this;
    }
}
