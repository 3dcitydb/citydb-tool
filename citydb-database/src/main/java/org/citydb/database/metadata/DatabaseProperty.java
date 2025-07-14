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

package org.citydb.database.metadata;

import java.util.Objects;
import java.util.Optional;

public class DatabaseProperty {
    private final String id;
    private final String name;
    private final String value;

    private DatabaseProperty(String id, String name, String value) {
        this.id = Objects.requireNonNull(id, "The database property id must not be null.");
        this.name = Objects.requireNonNull(name, "The database property name must not be null.");
        this.value = value;
    }

    public static DatabaseProperty of(String id, String name, String value) {
        return new DatabaseProperty(id, name, value);
    }

    public static DatabaseProperty of(String id, String name) {
        return new DatabaseProperty(id, name, null);
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Optional<String> getValue() {
        return Optional.ofNullable(value);
    }

    @Override
    public String toString() {
        return name + " " + (value != null ? value : "n/a");
    }
}
