/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.model.property;

import org.citydb.model.address.Address;
import org.citydb.model.common.Child;
import org.citydb.model.common.InlineOrByReferenceProperty;
import org.citydb.model.common.Name;
import org.citydb.model.feature.Feature;

import java.util.Objects;
import java.util.Optional;

public class AddressProperty extends Property<AddressProperty> implements InlineOrByReferenceProperty<Address> {
    private Address address;
    private String reference;

    private AddressProperty(Name name, Address address) {
        super(name, DataType.ADDRESS_PROPERTY);
        Objects.requireNonNull(address, "The address must not be null.");
        this.address = asChild(address);
    }

    private AddressProperty(Name name, String reference) {
        super(name, DataType.ADDRESS_PROPERTY);
        Objects.requireNonNull(reference, "The reference must not be null.");
        this.reference = reference;
    }

    public static AddressProperty of(Name name, Address address) {
        return new AddressProperty(name, address);
    }

    public static AddressProperty of(Name name, String reference) {
        return new AddressProperty(name, reference);
    }

    public static AddressProperty asReference(Name name, Address address) {
        Objects.requireNonNull(address, "The referenced address must not be null.");
        return new AddressProperty(name, address.getOrCreateObjectId());
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
    public Optional<String> getReference() {
        return Optional.ofNullable(reference);
    }

    @Override
    public AddressProperty setReference(String reference) {
        if (reference != null) {
            this.reference = reference;
            address = null;
        }

        return this;
    }

    @Override
    public AddressProperty setReference(Address address) {
        return address != null ? setReference(address.getOrCreateObjectId()) : this;
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
