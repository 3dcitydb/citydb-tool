/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.model.change;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public enum TransactionType {
    INSERT("insert"),
    UPDATE("update"),
    TERMINATE("terminate"),
    DELETE("delete");

    private final static Map<String, TransactionType> types = new HashMap<>();
    private final String value;

    static {
        Arrays.stream(values()).forEach(type -> types.put(type.value, type));
    }

    TransactionType(String value) {
        this.value = value;
    }

    public String getDatabaseValue() {
        return value.toUpperCase(Locale.ROOT);
    }

    public static TransactionType fromDatabaseValue(String value) {
        return value != null ?
                types.get(value.toLowerCase(Locale.ROOT)) :
                null;
    }

    @Override
    public String toString() {
        return value;
    }
}
