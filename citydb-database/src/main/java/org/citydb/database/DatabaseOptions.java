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

package org.citydb.database;

import com.alibaba.fastjson2.annotation.JSONField;
import org.citydb.config.SerializableConfig;
import org.citydb.database.connection.ConnectionDetails;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@SerializableConfig(name = "databaseOptions")
public class DatabaseOptions {
    private Map<String, ConnectionDetails> connections;
    @JSONField(name = "defaultConnection")
    private String defaultConnectionId;

    public Map<String, ConnectionDetails> getConnections() {
        if (connections == null) {
            connections = new HashMap<>();
        }

        return connections;
    }

    public DatabaseOptions setConnections(Map<String, ConnectionDetails> connections) {
        this.connections = connections;
        return this;
    }

    public String getDefaultConnectionId() {
        return defaultConnectionId;
    }

    public DatabaseOptions setDefaultConnectionId(String defaultConnectionId) {
        this.defaultConnectionId = defaultConnectionId;
        return this;
    }

    public Optional<ConnectionDetails> getDefaultConnection() {
        if (connections != null) {
            if (defaultConnectionId != null) {
                return Optional.ofNullable(connections.get(defaultConnectionId));
            } else if (connections.size() == 1) {
                return Optional.of(connections.values().iterator().next());
            }
        }

        return Optional.empty();
    }

    public DatabaseOptions setDefaultConnection(ConnectionDetails connectionDetails) {
        if (connectionDetails != null) {
            defaultConnectionId = getConnections().entrySet().stream()
                    .filter(e -> e.getValue() == connectionDetails)
                    .findAny().map(Map.Entry::getKey).orElse(null);
            if (defaultConnectionId == null) {
                defaultConnectionId = UUID.randomUUID().toString().substring(0, 7);
                connections.put(defaultConnectionId, connectionDetails);
            }
        }

        return this;
    }
}
