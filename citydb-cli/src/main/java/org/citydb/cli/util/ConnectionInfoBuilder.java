/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.cli.util;

import org.citydb.core.json.JsonMapping;
import org.citydb.database.DatabaseConstants;
import org.citydb.database.adapter.DatabaseAdapter;
import org.citydb.database.connection.ConnectionDetails;
import tools.jackson.databind.node.ObjectNode;

public class ConnectionInfoBuilder {

    private ConnectionInfoBuilder() {
    }

    public static ObjectNode build(DatabaseAdapter adapter) {
        ObjectNode info = objectNode();
        info.set("connection", buildDatabaseConnection(adapter.getConnectionDetails()));
        info.set("database", buildDatabase(adapter));
        return info;
    }

    private static ObjectNode buildDatabaseConnection(ConnectionDetails connectionDetails) {
        ObjectNode connection = objectNode()
                .put("databaseType", connectionDetails.getDatabaseType())
                .put("host", connectionDetails.getHost())
                .put("port", connectionDetails.getPort())
                .put("database", connectionDetails.getDatabase())
                .put("schema", connectionDetails.getSchema())
                .put("user", connectionDetails.getUser());

        if (connectionDetails.hasProperties()) {
            connection.set("properties", JsonMapping.mapper().valueToTree(connectionDetails.getProperties()));
        }

        return connection;
    }

    private static ObjectNode buildDatabase(DatabaseAdapter adapter) {
        ObjectNode database = objectNode()
                .put("name", DatabaseConstants.CITYDB_NAME)
                .put("version", adapter.getDatabaseMetadata().getVersion().toString());
        database.set("dbms", buildDbms(adapter));
        database.put("hasChangelogEnabled", adapter.getDatabaseMetadata().isChangelogEnabled());
        database.set("crs", buildCrs(adapter));
        return database;
    }

    private static ObjectNode buildDbms(DatabaseAdapter adapter) {
        ObjectNode dbms = objectNode()
                .put("name", adapter.getDatabaseMetadata().getVendorProductName())
                .put("version", adapter.getDatabaseMetadata().getVendorProductVersion());

        if (adapter.getDatabaseMetadata().hasProperties()) {
            dbms.set("properties", buildDbmsProperties(adapter));
        }

        return dbms;
    }

    private static ObjectNode buildDbmsProperties(DatabaseAdapter adapter) {
        ObjectNode properties = objectNode();
        adapter.getDatabaseMetadata().getProperties().forEach((id, property) -> properties.putObject(id)
                .put("name", property.getName())
                .put("value", property.getValue().orElse("n/a")));

        return properties;
    }

    private static ObjectNode buildCrs(DatabaseAdapter adapter) {
        return objectNode()
                .put("srid", adapter.getDatabaseMetadata().getSpatialReference().getSRID())
                .put("identifier", adapter.getDatabaseMetadata().getSpatialReference().getIdentifier())
                .put("name", adapter.getDatabaseMetadata().getSpatialReference().getName());
    }

    private static ObjectNode objectNode() {
        return JsonMapping.mapper().createObjectNode();
    }
}
