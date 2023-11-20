/*
 * citydb-tool - Command-line tool for the 3D City Database
 * https://www.3dcitydb.org/
 *
 * Copyright 2022-2023
 * Virtual City Systems, Germany
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

public class ConnectionDetails {
    private final PoolOptions poolOptions = new PoolOptions();
    private String description;
    private String databaseName;
    private String user;
    private String password;
    private String host;
    private int port;
    private String database;
    private String schema;

    public String getDescription() {
        return description;
    }

    public ConnectionDetails setDescription(String description) {
        this.description = description;
        return this;
    }

    public String getDatabaseName() {
        return databaseName;
    }

    public ConnectionDetails setDatabaseName(String databaseName) {
        this.databaseName = databaseName;
        return this;
    }

    public String getUser() {
        return user;
    }

    public ConnectionDetails setUser(String user) {
        this.user = user;
        return this;
    }

    public String getPassword() {
        return password;
    }

    public ConnectionDetails setPassword(String password) {
        this.password = password;
        return this;
    }

    public String getHost() {
        return host;
    }

    public ConnectionDetails setHost(String host) {
        this.host = host;
        return this;
    }

    public int getPort() {
        return port;
    }

    public ConnectionDetails setPort(int port) {
        this.port = port;
        return this;
    }

    public String getDatabase() {
        return database;
    }

    public ConnectionDetails setDatabase(String database) {
        this.database = database;
        return this;
    }

    public String getSchema() {
        return schema;
    }

    public ConnectionDetails setSchema(String schema) {
        this.schema = schema;
        return this;
    }

    public PoolOptions getPoolOptions() {
        return poolOptions;
    }

    public String toConnectString() {
        return user + "@" + host + ":" + port + "/" + database;
    }
}
