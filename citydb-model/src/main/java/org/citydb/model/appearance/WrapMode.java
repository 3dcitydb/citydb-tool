/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.model.appearance;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public enum WrapMode {
    NONE("none"),
    WRAP("wrap"),
    MIRROR("mirror"),
    CLAMP("clamp"),
    BORDER("border");

    private final static Map<String, WrapMode> modes = new HashMap<>();
    private final String value;

    static {
        Arrays.stream(values()).forEach(mode -> modes.put(mode.value, mode));
    }

    WrapMode(String value) {
        this.value = value;
    }

    public String getDatabaseValue() {
        return value;
    }

    public static WrapMode fromDatabaseValue(String value) {
        return value != null ?
                modes.get(value.toLowerCase(Locale.ROOT)) :
                null;
    }
}
