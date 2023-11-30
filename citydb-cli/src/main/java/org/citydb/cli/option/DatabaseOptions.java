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

import org.citydb.cli.util.CliConstants;
import org.citydb.database.connection.ConnectionDetails;
import picocli.CommandLine;

import java.util.Objects;

public class DatabaseOptions implements Option {
    @CommandLine.Option(names = {"-H", "--db-host"}, required = true,
            defaultValue = "${env:" + CliConstants.ENV_CITYDB_HOST + "}",
            description = "Name of the host on which the 3DCityDB is running.")
    private String host;

    @CommandLine.Option(names = {"-P", "--db-port"},
            defaultValue = "${env:" + CliConstants.ENV_CITYDB_PORT + "}",
            description = "Port of the 3DCityDB server (default: 5432).")
    private Integer port;

    @CommandLine.Option(names = {"-d", "--db-name"}, required = true,
            defaultValue = "${env:" + CliConstants.ENV_CITYDB_NAME + "}",
            description = "Name of the 3DCityDB database to connect to.")
    private String name;

    @CommandLine.Option(names = {"-S", "--db-schema"},
            defaultValue = "${env:" + CliConstants.ENV_CITYDB_SCHEMA + "}",
            description = "Schema to use when connecting to the 3DCityDB (default: citydb | username).")
    private String schema;

    @CommandLine.Option(names = {"-u", "--db-username"}, paramLabel = "<name>", required = true,
            defaultValue = "${env:" + CliConstants.ENV_CITYDB_USERNAME + "}",
            description = "Username to use when connecting to the 3DCityDB.")
    private String user;

    @CommandLine.Option(names = {"-p", "--db-password"}, arity = "0..1",
            defaultValue = "${env:" + CliConstants.ENV_CITYDB_PASSWORD + "}",
            description = "Password to use when connecting to the 3DCityDB (leave empty to be prompted).")
    private String password;

    public int getPort() {
        return Objects.requireNonNullElse(port, 5432);
    }

    public ConnectionDetails toConnectionDetails() {
        return new ConnectionDetails()
                .setDatabaseName("PostgreSQL")
                .setSchema(schema)
                .setHost(host)
                .setPort(getPort())
                .setDatabase(name)
                .setUser(user)
                .setPassword(password);
    }
}
