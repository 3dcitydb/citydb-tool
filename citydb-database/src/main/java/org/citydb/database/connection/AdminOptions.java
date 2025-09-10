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
