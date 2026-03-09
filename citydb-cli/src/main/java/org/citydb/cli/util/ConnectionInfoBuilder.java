/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.cli.util;

import com.alibaba.fastjson2.JSONObject;
import org.citydb.database.DatabaseConstants;
import org.citydb.database.adapter.DatabaseAdapter;
import org.citydb.database.connection.ConnectionDetails;

public class ConnectionInfoBuilder {

    private ConnectionInfoBuilder() {
    }

    public static JSONObject build(DatabaseAdapter adapter) {
        return new JSONObject().fluentPut("connection", buildDatabaseConnection(adapter.getConnectionDetails()))
                .fluentPut("database", buildDatabase(adapter));
    }

    private static JSONObject buildDatabaseConnection(ConnectionDetails connectionDetails) {
        JSONObject connection = new JSONObject().fluentPut("host", connectionDetails.getHost())
                .fluentPut("port", connectionDetails.getPort())
                .fluentPut("database", connectionDetails.getDatabase())
                .fluentPut("schema", connectionDetails.getSchema())
                .fluentPut("user", connectionDetails.getUser());

        if (connectionDetails.hasProperties()) {
            connection.put("properties", connectionDetails.getProperties());
        }

        return connection;
    }

    private static JSONObject buildDatabase(DatabaseAdapter adapter) {
        return new JSONObject().fluentPut("name", DatabaseConstants.CITYDB_NAME)
                .fluentPut("version", adapter.getDatabaseMetadata().getVersion().toString())
                .fluentPut("dbms", buildDbms(adapter))
                .fluentPut("hasChangelogEnabled", adapter.getDatabaseMetadata().isChangelogEnabled())
                .fluentPut("crs", buildCrs(adapter));
    }

    private static JSONObject buildDbms(DatabaseAdapter adapter) {
        JSONObject dbms = new JSONObject();
        dbms.fluentPut("name", adapter.getDatabaseMetadata().getVendorProductName())
                .fluentPut("version", adapter.getDatabaseMetadata().getVendorProductVersion());

        if (adapter.getDatabaseMetadata().hasProperties()) {
            dbms.put("properties", buildDbmsProperties(adapter));
        }

        return dbms;
    }

    private static JSONObject buildDbmsProperties(DatabaseAdapter adapter) {
        JSONObject properties = new JSONObject();
        adapter.getDatabaseMetadata().getProperties().forEach((id, property) -> {
            properties.putObject(id)
                    .fluentPut("name", property.getName())
                    .fluentPut("value", property.getValue().orElse("n/a"));
        });

        return properties;
    }

    private static JSONObject buildCrs(DatabaseAdapter adapter) {
        return new JSONObject().fluentPut("srid", adapter.getDatabaseMetadata().getSpatialReference().getSRID())
                .fluentPut("identifier", adapter.getDatabaseMetadata().getSpatialReference().getIdentifier())
                .fluentPut("name", adapter.getDatabaseMetadata().getSpatialReference().getName());
    }
}
