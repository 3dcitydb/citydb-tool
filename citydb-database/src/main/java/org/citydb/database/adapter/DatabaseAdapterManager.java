/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
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
