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

package org.citydb.io.citygml.adapter.address;

import org.citydb.model.address.Address;
import org.citydb.model.property.ArrayValue;
import org.xmlobjects.xal.model.*;
import org.xmlobjects.xal.model.types.*;

import java.util.Objects;

public class AddressSerializer {

    AddressSerializer() {
    }

    org.xmlobjects.xal.model.Address serialize(Address source) {
        org.xmlobjects.xal.model.Address address = null;

        String street = source.getStreet().orElse(null);
        String houseNumber = source.getHouseNumber().orElse(null);
        String poBox = source.getPoBox().orElse(null);
        String zipCode = source.getZipCode().orElse(null);
        String city = source.getCity().orElse(null);
        String state = source.getState().orElse(null);
        String country = source.getCountry().orElse(null);
        ArrayValue freeText = source.getFreeText().orElse(null);

        if (street != null
                || houseNumber != null
                || poBox != null
                || zipCode != null
                || city != null
                || state != null
                || country != null
                || freeText != null) {
            address = new org.xmlobjects.xal.model.Address();

            if (street != null) {
                Thoroughfare thoroughfare = new Thoroughfare();
                thoroughfare.setType("Street");
                thoroughfare.getNameElementOrNumber()
                        .add(new ThoroughfareNameOrNumber(new ThoroughfareName(street)));

                if (houseNumber != null) {
                    thoroughfare.getNameElementOrNumber()
                            .add(new ThoroughfareNameOrNumber(new Identifier(houseNumber)));
                }

                address.setThoroughfare(thoroughfare);
            }

            if (poBox != null) {
                PostalDeliveryPoint postalDeliveryPoint = new PostalDeliveryPoint(PostalDeliveryPointType.PO_BOX);
                postalDeliveryPoint.getIdentifiers().add(new Identifier(poBox));
                address.setPostalDeliveryPoint(postalDeliveryPoint);
            }

            if (zipCode != null) {
                PostCode postCode = new PostCode();
                postCode.getIdentifiers().add(new Identifier(zipCode));
                address.setPostCode(postCode);
            }

            if (city != null) {
                Locality locality = new Locality(LocalityType.TOWN);
                locality.getNameElements().add(new LocalityName(city));
                address.setLocality(locality);
            }

            if (state != null) {
                AdministrativeArea administrativeArea = new AdministrativeArea(AdministrativeAreaType.STATE);
                administrativeArea.getNameElements().add(new AdministrativeAreaName(state));
                address.setAdministrativeArea(administrativeArea);
            }

            if (country != null) {
                Country countryType = new Country();
                countryType.getNameElements().add(new CountryName(country));
                address.setCountry(countryType);
            }

            if (freeText != null && !freeText.isEmpty()) {
                address = new org.xmlobjects.xal.model.Address();

                FreeTextAddress freeTextAddress = new FreeTextAddress();
                freeText.getValues().stream()
                        .map(value -> value.asString(null))
                        .filter(Objects::nonNull)
                        .map(AddressLine::new)
                        .forEach(freeTextAddress.getAddressLines()::add);

                address.setFreeTextAddress(freeTextAddress);
            }
        }

        return address;
    }
}
