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

package org.citydb.cli.option;

import org.citydb.database.connection.ConnectionDetails;
import picocli.CommandLine;

public class ConnectionOptions implements Option {
    @CommandLine.Option(names = {"-H", "--db-host"},
            description = "Name of the host on which the 3DCityDB is running.")
    private String host;

    @CommandLine.Option(names = {"-P", "--db-port"},
            description = "Port of the 3DCityDB server (default: 5432).")
    private Integer port;

    @CommandLine.Option(names = {"-d", "--db-name"},
            description = "Name of the 3DCityDB database to connect to.")
    private String database;

    @CommandLine.Option(names = {"-S", "--db-schema"},
            description = "Schema to use when connecting to the 3DCityDB (default: citydb | username).")
    private String schema;

    @CommandLine.Option(names = {"-u", "--db-username"},
            description = "Username to use when connecting to the 3DCityDB.")
    private String user;

    @CommandLine.Option(names = {"-p", "--db-password"}, arity = "0..1",
            description = "Password to use when connecting to the 3DCityDB (leave empty to be prompted).")
    private String password;

    public String getHost() {
        return host;
    }

    public Integer getPort() {
        return port;
    }

    public String getDatabase() {
        return database;
    }

    public String getSchema() {
        return schema;
    }

    public String getUser() {
        return user;
    }

    public String getPassword() {
        return password;
    }

    public ConnectionDetails toConnectionDetails() {
        return new ConnectionDetails()
                .setHost(host)
                .setPort(port)
                .setDatabase(database)
                .setSchema(schema)
                .setUser(user)
                .setPassword(password);
    }
}
