/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.attribute;

import org.citydb.model.address.Address;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;

/**
 * The single source of truth for the {@code ADDRESS/<field>} projection
 * surface. Each constant binds one user-facing field name (CLI grammar,
 * camelCase) to the {@code Optional}-unwrapping extractor on
 * {@link Address}. Adding a new projectable address field = adding
 * one constant; parser and encoder pick it up via {@link #forName} and
 * {@link #names()}.
 */
enum AddressField {
    STREET("street", a -> a.getStreet().orElse(null)),
    HOUSE_NUMBER("houseNumber", a -> a.getHouseNumber().orElse(null)),
    PO_BOX("poBox", a -> a.getPoBox().orElse(null)),
    ZIP_CODE("zipCode", a -> a.getZipCode().orElse(null)),
    CITY("city", a -> a.getCity().orElse(null)),
    STATE("state", a -> a.getState().orElse(null)),
    COUNTRY("country", a -> a.getCountry().orElse(null)),
    // ArrayValue has no toString() override, so Object::toString would emit
    // ClassName@hashcode garbage. AttributeEncoder.formatArrayValue joins
    // the raw element values with "; " (same separator the ALL aggregate
    // uses), giving a human-readable cell.
    FREE_TEXT("freeText", a -> AttributeEncoder.formatArrayValue(a.getFreeText().orElse(null)));

    // Case-insensitive lookup (matches the table / aggregate / value-type-cast
    // keywords). TreeMap keeps the canonical camelCase strings as keys so
    // names() / "Allowed: ..." error hints display the official spelling.
    private static final Map<String, AddressField> BY_NAME;
    static {
        Map<String, AddressField> map = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (AddressField af : values()) {
            map.put(af.fieldName, af);
        }
        BY_NAME = Collections.unmodifiableMap(map);
    }

    private final String fieldName;
    private final Function<Address, Object> extractor;

    AddressField(String fieldName, Function<Address, Object> extractor) {
        this.fieldName = fieldName;
        this.extractor = extractor;
    }

    String fieldName() {
        return fieldName;
    }

    Object extract(Address address) {
        return extractor.apply(address);
    }

    /** Resolve the user-facing name (camelCase, case-insensitive) to a constant, or null if unknown. */
    static AddressField forName(String name) {
        return name == null ? null : BY_NAME.get(name);
    }

    /** Snapshot of every supported field name, used by the parser for the "Allowed: ..." error hint. */
    static Set<String> names() {
        return BY_NAME.keySet();
    }
}
