/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.database.schema;

import java.util.*;

public enum SimpleType implements ColumnType {
    BOOLEAN("boolean"),
    INTEGER("integer"),
    DOUBLE("double"),
    STRING("string"),
    TIMESTAMP("timestamp"),
    ARRAY("array"),
    BOOLEAN_ARRAY("booleanArray"),
    INTEGER_ARRAY("integerArray"),
    DOUBLE_ARRAY("doubleArray"),
    STRING_ARRAY("stringArray");

    public static final EnumSet<SimpleType> JOIN_CONDITION_TYPES = EnumSet.of(INTEGER, DOUBLE, STRING);

    private final static Map<String, SimpleType> types = new HashMap<>();
    private final String identifier;

    static {
        Arrays.stream(values()).forEach(type -> types.put(type.identifier.toLowerCase(Locale.ROOT), type));
    }

    SimpleType(String identifier) {
        this.identifier = identifier;
    }

    public static SimpleType of(String identifier) {
        return identifier != null ? types.get(identifier.toLowerCase(Locale.ROOT)) : null;
    }

    @Override
    public String getIdentifier() {
        return identifier;
    }

    @Override
    public String toString() {
        return identifier;
    }
}
