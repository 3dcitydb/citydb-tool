/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.database.connection;

import org.citydb.database.DatabaseConstants;

public class AdminOptions {
    private String user;
    private String password;
    private String database;

    public static AdminOptions of(AdminOptions other) {
        return new AdminOptions()
                .setUser(other.user)
                .setPassword(other.password)
                .setDatabase(other.database);
    }

    public String getUser() {
        return user;
    }

    public AdminOptions setUser(String user) {
        this.user = user;
        return this;
    }

    public AdminOptions setUserIfAbsent(String user) {
        return this.user == null ? setUser(user) : this;
    }

    public String getPassword() {
        return password;
    }

    public AdminOptions setPassword(String password) {
        this.password = password;
        return this;
    }

    public AdminOptions setPasswordIfAbsent(String password) {
        return this.password == null ? setPassword(password) : this;
    }

    public String getDatabase() {
        return database;
    }

    public AdminOptions setDatabase(String database) {
        this.database = database;
        return this;
    }

    public AdminOptions setDatabaseIfAbsent(String database) {
        return this.database == null ? setDatabase(database) : this;
    }

    public AdminOptions fillAbsentValuesFrom(AdminOptions other) {
        return other != null ?
                setUserIfAbsent(other.user)
                        .setPasswordIfAbsent(other.password)
                        .setDatabaseIfAbsent(other.database) :
                this;
    }

    public AdminOptions fillAbsentValuesFromEnv() {
        return setUserIfAbsent(System.getenv(DatabaseConstants.ENV_CITYDB_ADMIN_USERNAME))
                .setPasswordIfAbsent(System.getenv(DatabaseConstants.ENV_CITYDB_ADMIN_PASSWORD))
                .setDatabaseIfAbsent(System.getenv(DatabaseConstants.ENV_CITYDB_ADMIN_DB));
    }

    boolean hasValues() {
        return user != null
                || password != null
                || database != null;
    }
}
