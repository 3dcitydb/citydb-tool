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

    public String getDescriptionOrElse(String defaultValue) {
        return description != null ? description : defaultValue;
    }

    public ConnectionDetails setDescription(String description) {
        this.description = description;
        return this;
    }

    public String getDatabaseName() {
        return databaseName;
    }

    public String getDatabaseNameOrElse(String defaultValue) {
        return databaseName != null ? databaseName : defaultValue;
    }

    public ConnectionDetails setDatabaseName(String databaseName) {
        this.databaseName = databaseName;
        return this;
    }

    public String getUser() {
        return user;
    }

    public String getUserOrElse(String defaultValue) {
        return user != null ? user : defaultValue;
    }

    public ConnectionDetails setUser(String user) {
        this.user = user;
        return this;
    }

    public String getPassword() {
        return password;
    }

    public String getPasswordOrElse(String defaultValue) {
        return password != null ? password : defaultValue;
    }

    public ConnectionDetails setPassword(String password) {
        this.password = password;
        return this;
    }

    public String getHost() {
        return host;
    }

    public String getHostOrElse(String defaultValue) {
        return host != null ? host : defaultValue;
    }

    public ConnectionDetails setHost(String host) {
        this.host = host;
        return this;
    }

    public Integer getPort() {
        return port;
    }

    public Integer getPortOrElse(Integer defaultValue) {
        return port != null ? port : defaultValue;
    }

    public Integer getPortOrElse(String defaultValue) {
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

    public String getDatabaseOrElse(String defaultValue) {
        return database != null ? database : defaultValue;
    }

    public ConnectionDetails setDatabase(String database) {
        this.database = database;
        return this;
    }

    public String getSchema() {
        return schema;
    }

    public String getSchemaOrElse(String defaultValue) {
        return schema != null ? schema : defaultValue;
    }

    public ConnectionDetails setSchema(String schema) {
        this.schema = schema;
        return this;
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

    private ConnectionDetails setProperties(String properties) {
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

    public ConnectionDetails fillAbsentValuesFromEnv() {
        return setUser(getUserOrElse(System.getenv(DatabaseConstants.ENV_CITYDB_USERNAME)))
                .setPassword(getPasswordOrElse(System.getenv(DatabaseConstants.ENV_CITYDB_PASSWORD)))
                .setHost(getHostOrElse(System.getenv(DatabaseConstants.ENV_CITYDB_HOST)))
                .setPort(getPortOrElse(System.getenv(DatabaseConstants.ENV_CITYDB_PORT)))
                .setDatabase(getDatabaseOrElse(System.getenv(DatabaseConstants.ENV_CITYDB_NAME)))
                .setSchema(getSchemaOrElse(System.getenv(DatabaseConstants.ENV_CITYDB_SCHEMA)))
                .setProperties(System.getenv(DatabaseConstants.ENV_CITYDB_CONN_PROPS));
    }

    public String toConnectString() {
        return user + "@" + host + (port != null ? ":" + port : "") + "/" + database;
    }
}
