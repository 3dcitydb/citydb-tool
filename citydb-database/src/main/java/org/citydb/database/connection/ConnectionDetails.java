/*
 * citydb-tool - Command-line tool for the 3D City Database
 * https://www.3dcitydb.org/
 *
 * Copyright 2022-2023
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

import java.util.Optional;

public class ConnectionDetails {
    private String description;
    private String databaseName;
    private String user;
    private String password;
    private String host;
    private Integer port;
    private String database;
    private String schema;
    private PoolOptions poolOptions;

    public String getDescription() {
        return description;
    }

    public String getDescription(String defaultValue) {
        return description != null ? description : defaultValue;
    }

    public ConnectionDetails setDescription(String description) {
        this.description = description;
        return this;
    }

    public String getDatabaseName() {
        return databaseName;
    }

    public String getDatabaseName(String defaultValue) {
        return databaseName != null ? databaseName : defaultValue;
    }

    public ConnectionDetails setDatabaseName(String databaseName) {
        this.databaseName = databaseName;
        return this;
    }

    public String getUser() {
        return user;
    }

    public String getUser(String defaultValue) {
        return user != null ? user : defaultValue;
    }

    public ConnectionDetails setUser(String user) {
        this.user = user;
        return this;
    }

    public String getPassword() {
        return password;
    }

    public String getPassword(String defaultValue) {
        return password != null ? password : defaultValue;
    }

    public ConnectionDetails setPassword(String password) {
        this.password = password;
        return this;
    }

    public String getHost() {
        return host;
    }

    public String getHost(String defaultValue) {
        return host != null ? host : defaultValue;
    }

    public ConnectionDetails setHost(String host) {
        this.host = host;
        return this;
    }

    public Integer getPort() {
        return port;
    }

    public Integer getPort(Integer defaultValue) {
        return port != null ? port : defaultValue;
    }

    public Integer getPort(String defaultValue) {
        if (port != null) {
            return port;
        } else {
            try {
                return Integer.parseInt(defaultValue);
            } catch (NumberFormatException e) {
                return null;
            }
        }
    }

    public ConnectionDetails setPort(Integer port) {
        this.port = port;
        return this;
    }

    public String getDatabase() {
        return database;
    }

    public String getDatabase(String defaultValue) {
        return database != null ? database : defaultValue;
    }

    public ConnectionDetails setDatabase(String database) {
        this.database = database;
        return this;
    }

    public String getSchema() {
        return schema;
    }

    public String getSchema(String defaultValue) {
        return schema != null ? schema : defaultValue;
    }

    public ConnectionDetails setSchema(String schema) {
        this.schema = schema;
        return this;
    }

    public Optional<PoolOptions> getPoolOptions() {
        return Optional.ofNullable(poolOptions);
    }

    public ConnectionDetails setPoolOptions(PoolOptions poolOptions) {
        this.poolOptions = poolOptions;
        return this;
    }

    public String toConnectString() {
        return user + "@" + host + ":" + port + "/" + database;
    }
}
