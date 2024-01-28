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

package org.citydb.io.citygml.adapter.address;

import org.citydb.model.address.Address;
import org.citydb.model.property.ArrayValue;
import org.citydb.model.property.Value;
import org.xmlobjects.xal.model.*;
import org.xmlobjects.xal.model.types.*;
import org.xmlobjects.xal.visitor.XALWalker;

import java.util.Objects;

public class AddressBuilder extends XALWalker {
    private Address target;

    AddressBuilder() {
    }

    void build(org.xmlobjects.xal.model.Address source, Address target) {
        try {
            this.target = target;
            source.accept(this);
        } finally {
            this.target = null;
        }
    }

    @Override
    public void visit(AdministrativeArea administrativeArea) {
        if (administrativeArea.isSetNameElements()) {
            ContentBuilder builder = new ContentBuilder(", ");
            administrativeArea.getNameElements().stream()
                    .filter(element -> element.getNameType() != AdministrativeAreaNameType.REFERENCE_LOCATION)
                    .forEach(builder::add);
            if (administrativeArea.getType() == AdministrativeAreaType.CITY) {
                target.setCity(builder.build());
            } else {
                target.setState(builder.build());
            }
        }
    }

    @Override
    public void visit(Country country) {
        if (country.isSetNameElements()) {
            ContentBuilder builder = new ContentBuilder(", ");
            country.getNameElements().forEach(builder::add);
            target.setCountry(builder.build());
        }
    }

    @Override
    public void visit(FreeTextAddress freeTextAddress) {
        if (freeTextAddress.isSetAddressLines()) {
            ArrayValue freeText = ArrayValue.newInstance();
            freeTextAddress.getAddressLines().stream()
                    .map(AddressLine::getContent)
                    .filter(Objects::nonNull)
                    .map(Value::of)
                    .forEach(freeText::add);
            if (!freeText.isEmpty()) {
                target.setFreeText(freeText);
            }
        }
    }

    @Override
    public void visit(Locality locality) {
        if (locality.isSetNameElements()) {
            ContentBuilder builder = new ContentBuilder(", ");
            locality.getNameElements().stream()
                    .filter(element -> element.getNameType() != LocalityNameType.REFERENCE_LOCATION)
                    .forEach(builder::add);
            target.setCity(builder.build());
        }
    }

    @Override
    public void visit(PostalDeliveryPoint postalDeliveryPoint) {
        if ((postalDeliveryPoint.getType() == PostalDeliveryPointType.GPO_BOX
                || postalDeliveryPoint.getType() == PostalDeliveryPointType.PO_BOX)
                && postalDeliveryPoint.isSetIdentifiers()) {
            ContentBuilder builder = new ContentBuilder();
            postalDeliveryPoint.getIdentifiers().forEach(builder::add);
            target.setPoBox(builder.build());
        }
    }

    @Override
    public void visit(PostCode postCode) {
        if (postCode.isSetIdentifiers()) {
            ContentBuilder builder = new ContentBuilder();
            postCode.getIdentifiers().forEach(builder::add);
            target.setZipCode(builder.build());
        }
    }

    @Override
    public void visit(Thoroughfare thoroughfare) {
        if (thoroughfare.isSetNameElementOrNumber()) {
            ContentBuilder nameBuilder = new ContentBuilder();
            ContentBuilder preDirectionBuilder = new ContentBuilder();
            ContentBuilder postDirectionBuilder = new ContentBuilder();
            ContentBuilder identifierBuilder = new ContentBuilder();

            for (ThoroughfareNameOrNumber nameOrNumber : thoroughfare.getNameElementOrNumber()) {
                if (nameOrNumber.isSetNameElement()) {
                    ThoroughfareName name = nameOrNumber.getNameElement();
                    if (name.getNameType() == ThoroughfareNameType.PRE_DIRECTION) {
                        preDirectionBuilder.add(name);
                    } else if (name.getNameType() == ThoroughfareNameType.POST_DIRECTION) {
                        postDirectionBuilder.add(name);
                    } else if (name.getNameType() != ThoroughfareNameType.REFERENCE_LOCATION) {
                        nameBuilder.add(name);
                    }
                } else if (nameOrNumber.isSetNumber()) {
                    identifierBuilder.add(nameOrNumber.getNumber());
                }
            }

            target.setStreet(preDirectionBuilder
                    .add(nameBuilder)
                    .add(postDirectionBuilder)
                    .build());
            target.setHouseNumber(identifierBuilder.build());
        }
    }

    private static final class ContentBuilder {
        private final String delimiter;
        private StringBuilder builder;

        ContentBuilder(String delimiter) {
            this.delimiter = delimiter != null ? delimiter : " ";
        }

        ContentBuilder() {
            this(" ");
        }

        ContentBuilder add(Name<?> name) {
            add(name.getContent());
            return this;
        }

        ContentBuilder add(Identifier identifier) {
            add(identifier.getContent());
            return this;
        }
        
        ContentBuilder add(ContentBuilder other) {
            add(other.build());
            return this;
        }

        ContentBuilder add(String content) {
            if (content != null) {
                if (builder == null) {
                    builder = new StringBuilder(content);
                } else {
                    builder.append(delimiter).append(content);
                }
            }
            
            return this;
        }

        public String build() {
            return builder != null ? builder.toString() : null;
        }
    }
}
