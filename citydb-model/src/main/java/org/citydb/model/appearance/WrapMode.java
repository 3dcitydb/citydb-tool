/*
 * citydb-tool - Command-line tool for the 3D City Database
 * https://www.3dcitydb.org/
 *
 * Copyright 2022-2023
 * Virtual City Systems, Germany
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
