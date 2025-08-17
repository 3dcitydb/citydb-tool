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

package org.citydb.cli.connect;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.citydb.database.DatabaseConstants;
import org.citydb.database.adapter.DatabaseAdapter;
import org.citydb.database.connection.ConnectionDetails;

import java.util.LinkedHashSet;
import java.util.Set;

public class ConnectionStatusBuilder {

    private ConnectionStatusBuilder() {
    }

    public static JSONObject buildSuccess(DatabaseAdapter adapter) {
        return new JSONObject().fluentPut("connectionStatus", "success")
                .fluentPut("connection", buildDatabaseConnection(adapter.getConnectionDetails()))
                .fluentPut("database", buildDatabase(adapter));
    }

    public static JSONObject buildFailure(ConnectionDetails connectionDetails, Exception e) {
        return new JSONObject().fluentPut("connectionStatus", "failure")
                .fluentPut("connection", buildDatabaseConnection(connectionDetails))
                .fluentPut("error", buildError(e));
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

    private static JSONObject buildError(Exception e) {
        return new JSONObject().fluentPut("causes", buildCauses(e));
    }

    private static JSONArray buildCauses(Exception e) {
        Throwable cause = e;
        JSONArray causes = new JSONArray();
        Set<String> messages = new LinkedHashSet<>();

        do {
            String message = cause.getMessage();
            if (message != null && messages.add(message)) {
                causes.add(new JSONObject().fluentPut("message", message)
                        .fluentPut("exception", cause.getClass().getName()));
            }
        } while ((cause = cause.getCause()) != null);

        return causes;
    }
}
