/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
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

    public boolean hasConnections() {
        return connections != null && !connections.isEmpty();
    }

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

    public Optional<String> getDefaultConnectionId() {
        return Optional.ofNullable(defaultConnectionId);
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
