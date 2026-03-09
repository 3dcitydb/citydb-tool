/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.model.appearance;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public enum TextureType {
    SPECIFIC("specific"),
    TYPICAL("typical"),
    UNKNOWN("unknown");

    private final static Map<String, TextureType> types = new HashMap<>();
    private final String value;

    static {
        Arrays.stream(values()).forEach(type -> types.put(type.value, type));
    }

    TextureType(String value) {
        this.value = value;
    }

    public String getDatabaseValue() {
        return value;
    }

    public static TextureType fromDatabaseValue(String value) {
        return value != null ?
                types.get(value.toLowerCase(Locale.ROOT)) :
                null;
    }
}
