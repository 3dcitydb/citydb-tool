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

package org.citydb.database.schema;

import org.citydb.model.common.Name;
import org.citydb.model.common.Namespaces;

public enum ValidityTime {
    DATABASE_TIME("databaseTime", "creationDate", "terminationDate"),
    REAL_WORLD_TIME("realWorldTime", "validFrom", "validTo");

    private final String value;
    private final Name from;
    private final Name to;

    ValidityTime(String value, String from, String to) {
        this.value = value;
        this.from = Name.of(from, Namespaces.CORE);
        this.to = Name.of(to, Namespaces.CORE);
    }

    public Name from() {
        return from;
    }

    public Name to() {
        return to;
    }

    public String toValue() {
        return value;
    }

    public static ValidityTime fromValue(String value) {
        for (ValidityTime v : ValidityTime.values()) {
            if (v.value.equals(value)) {
                return v;
            }
        }

        return null;
    }

    @Override
    public String toString() {
        return value;
    }
}
