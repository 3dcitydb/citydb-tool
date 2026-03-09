/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
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
