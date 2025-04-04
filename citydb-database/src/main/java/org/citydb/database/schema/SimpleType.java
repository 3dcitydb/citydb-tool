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
