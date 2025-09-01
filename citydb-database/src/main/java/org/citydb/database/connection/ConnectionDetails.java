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

import java.util.HashMap;
import java.util.Map;
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
    private Map<String, Object> properties;
    private PoolOptions poolOptions;

    public static ConnectionDetails of(ConnectionDetails other) {
        return new ConnectionDetails()
                .setDescription(other.description)
                .setDatabaseName(other.databaseName)
                .setUser(other.user)
                .setPassword(other.password)
                .setHost(other.host)
                .setPort(other.port)
                .setDatabase(other.database)
                .setSchema(other.schema)
                .setProperties(other.properties != null ? new HashMap<>(other.properties) : null)
                .setPoolOptions(other.poolOptions != null ? PoolOptions.of(other.poolOptions) : null);
    }

    public String getDescription() {
        return description;
    }

    public ConnectionDetails setDescription(String description) {
        this.description = description;
        return this;
    }

    public ConnectionDetails setDescriptionIfAbsent(String description) {
        return this.description == null ? setDescription(description) : this;
    }

    public String getDatabaseName() {
        return databaseName;
    }

    public ConnectionDetails setDatabaseName(String databaseName) {
        this.databaseName = databaseName;
        return this;
    }

    public ConnectionDetails setDatabaseNameIfAbsent(String databaseName) {
        return this.databaseName == null ? setDatabaseName(databaseName) : this;
    }

    public String getUser() {
        return user;
    }

    public ConnectionDetails setUser(String user) {
        this.user = user;
        return this;
    }

    public ConnectionDetails setUserIfAbsent(String user) {
        return this.user == null ? setUser(user) : this;
    }

    public String getPassword() {
        return password;
    }

    public ConnectionDetails setPassword(String password) {
        this.password = password;
        return this;
    }

    public ConnectionDetails setPasswordIfAbsent(String password) {
        return this.password == null ? setPassword(password) : this;
    }

    public String getHost() {
        return host;
    }

    public ConnectionDetails setHost(String host) {
        this.host = host;
        return this;
    }

    public ConnectionDetails setHostIfAbsent(String host) {
        return this.host == null ? setHost(host) : this;
    }

    public Integer getPort() {
        return port;
    }

    public ConnectionDetails setPort(Integer port) {
        this.port = port;
        return this;
    }

    public ConnectionDetails setPortIfAbsent(Integer port) {
        return this.port == null ? setPort(port) : this;
    }

    public ConnectionDetails setPortIfAbsent(String port) {
        try {
            return this.port == null ? setPort(Integer.parseInt(port)) : this;
        } catch (NumberFormatException e) {
            return this;
        }
    }

    public String getDatabase() {
        return database;
    }

    public ConnectionDetails setDatabase(String database) {
        this.database = database;
        return this;
    }

    public ConnectionDetails setDatabaseIfAbsent(String database) {
        return this.database == null ? setDatabase(database) : this;
    }

    public String getSchema() {
        return schema;
    }

    public ConnectionDetails setSchema(String schema) {
        this.schema = schema;
        return this;
    }

    public ConnectionDetails setSchemaIfAbsent(String schema) {
        return this.schema == null ? setSchema(schema) : this;
    }

    public boolean hasProperties() {
        return properties != null && !properties.isEmpty();
    }

    public Map<String, Object> getProperties() {
        if (properties == null) {
            properties = new HashMap<>();
        }

        return properties;
    }

    public ConnectionDetails setProperties(Map<String, Object> properties) {
        this.properties = properties;
        return this;
    }

    public ConnectionDetails addPropertiesIfAbsent(Map<String, Object> properties) {
        if (properties != null && !properties.isEmpty()) {
            properties.forEach((key, value) -> getProperties().putIfAbsent(key, value));
        }

        return this;
    }

    private ConnectionDetails addPropertiesIfAbsent(String properties) {
        if (properties != null && !properties.isEmpty()) {
            for (String property : properties.split(",")) {
                String[] items = property.split("=", 2);
                if (items.length == 2) {
                    getProperties().putIfAbsent(items[0].trim(), items[1].trim());
                }
            }
        }

        return this;
    }

    public Optional<PoolOptions> getPoolOptions() {
        return Optional.ofNullable(poolOptions);
    }

    public ConnectionDetails setPoolOptions(PoolOptions poolOptions) {
        this.poolOptions = poolOptions;
        return this;
    }

    public ConnectionDetails setPoolOptionsIfAbsent(PoolOptions poolOptions) {
        return this.poolOptions == null ? setPoolOptions(poolOptions) : this;
    }

    public ConnectionDetails fillAbsentValuesFrom(ConnectionDetails other) {
        return other != null ?
                setDescriptionIfAbsent(other.description)
                        .setDatabaseNameIfAbsent(other.database)
                        .setUserIfAbsent(other.user)
                        .setPasswordIfAbsent(other.password)
                        .setHostIfAbsent(other.host)
                        .setPortIfAbsent(other.port)
                        .setDatabaseIfAbsent(other.database)
                        .setSchemaIfAbsent(other.schema)
                        .addPropertiesIfAbsent(other.properties)
                        .setPoolOptionsIfAbsent(other.poolOptions) :
                this;
    }

    public ConnectionDetails fillAbsentValuesFromEnv() {
        return setUserIfAbsent(System.getenv(DatabaseConstants.ENV_CITYDB_USERNAME))
                .setPasswordIfAbsent(System.getenv(DatabaseConstants.ENV_CITYDB_PASSWORD))
                .setHostIfAbsent(System.getenv(DatabaseConstants.ENV_CITYDB_HOST))
                .setPortIfAbsent(System.getenv(DatabaseConstants.ENV_CITYDB_PORT))
                .setDatabaseIfAbsent(System.getenv(DatabaseConstants.ENV_CITYDB_NAME))
                .setSchemaIfAbsent(System.getenv(DatabaseConstants.ENV_CITYDB_SCHEMA))
                .addPropertiesIfAbsent(System.getenv(DatabaseConstants.ENV_CITYDB_CONN_PROPS));
    }

    public String toConnectString() {
        return user + "@" + host + (port != null ? ":" + port : "") + "/" + database;
    }
}
