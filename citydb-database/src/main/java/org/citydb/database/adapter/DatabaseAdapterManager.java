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

package org.citydb.database.adapter;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.ServiceLoader;

public class DatabaseAdapterManager {
    private final Map<String, DatabaseAdapter> adapters;

    private DatabaseAdapterManager() {
        this.adapters = new HashMap<>();
    }

    public static DatabaseAdapterManager newInstance() {
        return new DatabaseAdapterManager();
    }

    public DatabaseAdapterManager load() throws DatabaseAdapterException {
        return load(Thread.currentThread().getContextClassLoader());
    }

    public DatabaseAdapterManager load(ClassLoader loader) throws DatabaseAdapterException {
        for (DatabaseAdapter adapter : ServiceLoader.load(DatabaseAdapter.class, loader)) {
            register(adapter, false);
        }

        return this;
    }

    public DatabaseAdapterManager register(DatabaseAdapter adapter) throws DatabaseAdapterException {
        return register(adapter, false);
    }

    public DatabaseAdapterManager register(DatabaseAdapter adapter, boolean overwriteExisting) throws DatabaseAdapterException {
        DatabaseType databaseType = adapter.getClass().getAnnotation(DatabaseType.class);
        if (databaseType == null) {
            throw new DatabaseAdapterException("No @DatabaseType definition provided for database adapter " +
                    adapter.getClass().getName() + ".");
        }

        DatabaseAdapter current = adapters.put(databaseType.name().toLowerCase(Locale.ROOT), adapter);
        if (!overwriteExisting && current != null) {
            throw new DatabaseAdapterException("Two database adapters are registered for the " +
                    "database '" + databaseType.name() + "' : " +
                    adapter.getClass().getName() + " and " + current.getClass().getName() + ".");
        }

        return this;
    }

    public DatabaseAdapter getAdapterForDatabase(String name) {
        return name != null ? adapters.get(name.toLowerCase(Locale.ROOT)) : null;
    }
}
