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

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONWriter;
import org.apache.logging.log4j.Logger;
import org.citydb.cli.ExecutionException;
import org.citydb.cli.common.Command;
import org.citydb.cli.common.ConfigOption;
import org.citydb.cli.common.ConnectionOptions;
import org.citydb.cli.common.JsonOutputOptions;
import org.citydb.cli.logging.LoggerManager;
import org.citydb.config.Config;
import org.citydb.config.ConfigException;
import org.citydb.database.DatabaseManager;
import org.citydb.database.DatabaseOptions;
import org.citydb.database.connection.ConnectionDetails;
import picocli.CommandLine;

import java.io.OutputStream;
import java.util.Objects;

@CommandLine.Command(
        name = "connect",
        description = "Test connection to the database.")
public class ConnectCommand implements Command {
    @CommandLine.Mixin
    private JsonOutputOptions outputOptions;

    @CommandLine.ArgGroup(exclusive = false,
            heading = "Database connection options:%n")
    private ConnectionOptions connectionOptions;

    @ConfigOption
    private Config config;

    private final Logger logger = LoggerManager.getInstance().getLogger(ConnectCommand.class);

    @Override
    public Integer call() throws ExecutionException {
        DatabaseManager databaseManager = DatabaseManager.newInstance();
        try {
            return doConnect(databaseManager);
        } finally {
            databaseManager.disconnect();
        }
    }

    private int doConnect(DatabaseManager databaseManager) throws ExecutionException {
        ConnectionDetails connectionDetails = Objects.requireNonNullElseGet(connectionOptions, ConnectionOptions::new)
                .toConnectionDetails(getDatabaseOptions())
                .fillAbsentValuesFromEnv();

        logger.info("Testing connection to database {}.", connectionDetails.toConnectString());
        if (outputOptions.isOutputSpecified()) {
            if (outputOptions.isWriteToStdout()) {
                logger.info("Writing connection status as JSON to standard output.");
            } else {
                logger.info("Writing connection status to JSON file {}.", outputOptions.getFile());
            }
        }

        try {
            databaseManager.connect(connectionDetails);
            handleSuccess(databaseManager);
            return CommandLine.ExitCode.OK;
        } catch (Exception e) {
            handleFailure(connectionDetails, e);
            return CommandLine.ExitCode.SOFTWARE;
        }
    }

    private void handleSuccess(DatabaseManager databaseManager) throws ExecutionException {
        logger.info("Connection successfully established.");
        if (outputOptions.isOutputSpecified()) {
            writeJson(ConnectionStatusBuilder.buildSuccess(databaseManager.getAdapter()));
        }

        if (!outputOptions.isWriteToStdout()) {
            databaseManager.reportDatabaseInfo(logger::info);
        }
    }

    private void handleFailure(ConnectionDetails connectionDetails, Exception e) throws ExecutionException {
        if (outputOptions.isOutputSpecified()) {
            if (outputOptions.isWriteToStdout()) {
                logger.error("Failed to connect to the database.");
            }

            writeJson(ConnectionStatusBuilder.buildFailure(connectionDetails, e));
        }

        if (!outputOptions.isWriteToStdout()) {
            throw new ExecutionException("Failed to connect to the database.", e);
        }
    }

    private void writeJson(JSONObject object) throws ExecutionException {
        try (OutputStream stream = outputOptions.openStream()) {
            JSON.writeTo(stream, object, JSONWriter.Feature.WriteNulls, JSONWriter.Feature.PrettyFormatWith2Space);
        } catch (Exception e) {
            throw new ExecutionException("Failed to write connection status as JSON.", e);
        }
    }

    private DatabaseOptions getDatabaseOptions() throws ExecutionException {
        try {
            return config.get(DatabaseOptions.class);
        } catch (ConfigException e) {
            throw new ExecutionException("Failed to get database options from config.", e);
        }
    }
}
