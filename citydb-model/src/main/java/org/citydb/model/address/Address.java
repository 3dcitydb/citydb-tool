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

package org.citydb.model.address;

import org.citydb.model.common.*;
import org.citydb.model.geometry.MultiPoint;
import org.citydb.model.property.ArrayValue;

import java.util.Optional;

public class Address extends Child implements Identifiable, Visitable, Describable<AddressDescriptor> {
    private String objectId;
    private String identifier;
    private String identifierCodeSpace;
    private String street;
    private String houseNumber;
    private String poBox;
    private String zipCode;
    private String city;
    private String state;
    private String country;
    private ArrayValue freeText;
    private MultiPoint multiPoint;
    private String genericContent;
    private String genericContentMimeType;
    private AddressDescriptor descriptor;

    private Address() {
    }

    public static Address newInstance() {
        return new Address();
    }

    @Override
    public Optional<String> getObjectId() {
        return Optional.ofNullable(objectId);
    }

    @Override
    public Address setObjectId(String objectId) {
        this.objectId = objectId;
        return this;
    }

    @Override
    public Optional<String> getIdentifier() {
        return Optional.ofNullable(identifier);
    }

    @Override
    public Address setIdentifier(String identifier) {
        this.identifier = identifier;
        return this;
    }

    @Override
    public Optional<String> getIdentifierCodeSpace() {
        return Optional.ofNullable(identifierCodeSpace);
    }

    @Override
    public Address setIdentifierCodeSpace(String identifierCodeSpace) {
        this.identifierCodeSpace = identifierCodeSpace;
        return this;
    }

    public Optional<String> getStreet() {
        return Optional.ofNullable(street);
    }

    public Address setStreet(String street) {
        this.street = street;
        return this;
    }

    public Optional<String> getHouseNumber() {
        return Optional.ofNullable(houseNumber);
    }

    public Address setHouseNumber(String houseNumber) {
        this.houseNumber = houseNumber;
        return this;
    }

    public Optional<String> getPoBox() {
        return Optional.ofNullable(poBox);
    }

    public Address setPoBox(String poBox) {
        this.poBox = poBox;
        return this;
    }

    public Optional<String> getZipCode() {
        return Optional.ofNullable(zipCode);
    }

    public Address setZipCode(String zipCode) {
        this.zipCode = zipCode;
        return this;
    }

    public Optional<String> getCity() {
        return Optional.ofNullable(city);
    }

    public Address setCity(String city) {
        this.city = city;
        return this;
    }

    public Optional<String> getState() {
        return Optional.ofNullable(state);
    }

    public Address setState(String state) {
        this.state = state;
        return this;
    }

    public Optional<String> getCountry() {
        return Optional.ofNullable(country);
    }

    public Address setCountry(String country) {
        this.country = country;
        return this;
    }

    public Optional<ArrayValue> getFreeText() {
        return Optional.ofNullable(freeText);
    }

    public Address setFreeText(ArrayValue freeText) {
        this.freeText = freeText;
        return this;
    }

    public Optional<MultiPoint> getMultiPoint() {
        return Optional.ofNullable(multiPoint);
    }

    public Address setMultiPoint(MultiPoint multiPoint) {
        this.multiPoint = multiPoint;
        return this;
    }

    public Optional<String> getGenericContent() {
        return Optional.ofNullable(genericContent);
    }

    public Address setGenericContent(String genericContent) {
        this.genericContent = genericContent;
        return this;
    }

    public Optional<String> getGenericContentMimeType() {
        return Optional.ofNullable(genericContentMimeType);
    }

    public Address setGenericContentMimeType(String genericContentMimeType) {
        this.genericContentMimeType = genericContentMimeType;
        return this;
    }

    @Override
    public Optional<AddressDescriptor> getDescriptor() {
        return Optional.ofNullable(descriptor);
    }

    @Override
    public Address setDescriptor(AddressDescriptor descriptor) {
        this.descriptor = descriptor;
        return this;
    }

    @Override
    public void accept(Visitor visitor) {
        visitor.visit(this);
    }
}
