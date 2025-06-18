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

package org.citydb.model.property;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public enum RelationType {
    RELATES(0),
    CONTAINS(1);

    private final static Map<Integer, RelationType> types = new HashMap<>();
    private final int value;

    static {
        Arrays.stream(values()).forEach(type -> types.put(type.value, type));
    }

    RelationType(int value) {
        this.value = value;
    }

    public int getDatabaseValue() {
        return value;
    }

    public static RelationType fromDatabaseValue(int value) {
        return types.getOrDefault(value, RELATES);
    }
}
