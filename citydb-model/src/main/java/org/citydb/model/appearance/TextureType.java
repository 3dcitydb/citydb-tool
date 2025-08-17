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
