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
import com.alibaba.fastjson2.JSONWriter;
import org.apache.logging.log4j.Logger;
import org.citydb.cli.ExecutionException;
import org.citydb.cli.common.Command;
import org.citydb.cli.common.ConfigOption;
import org.citydb.cli.common.ConnectionOptions;
import org.citydb.cli.logging.LoggerManager;
import org.citydb.cli.util.CommandHelper;
import org.citydb.cli.util.ConnectionInfoBuilder;
import org.citydb.config.Config;
import org.citydb.database.DatabaseManager;
import org.citydb.database.adapter.DatabaseAdapterManager;
import org.citydb.database.connection.ConnectionDetails;
import picocli.CommandLine;

import java.io.OutputStream;

@CommandLine.Command(
        name = "connect",
        description = "Test connection to the database.")
public class ConnectCommand implements Command {
    @CommandLine.Mixin
    private OutputOptions outputOptions;

    @CommandLine.ArgGroup(exclusive = false,
            heading = "Database connection options:%n")
    private ConnectionOptions connectionOptions;

    @ConfigOption
    private Config config;

    private final Logger logger = LoggerManager.getInstance().getLogger(ConnectCommand.class);
    private final CommandHelper helper = CommandHelper.getInstance();

    public Integer call() throws ExecutionException {
        ConnectionDetails connectionDetails = helper.getConnectionDetails(connectionOptions, config);
        DatabaseManager databaseManager = helper.getDatabaseManager();
        DatabaseAdapterManager databaseAdapterManager = helper.getDatabaseAdapterManager();

        try {
            logger.info("Testing connection to database {}.", connectionDetails.toConnectString());
            databaseManager.connect(connectionDetails, databaseAdapterManager);
        } catch (Exception e) {
            throw new ExecutionException("Failed to connect to the database.", e);
        }

        logger.info("Connection successfully established.");

        if (outputOptions.isOutputSpecified()) {
            logger.info("Writing connection info as JSON to {}.", outputOptions);
            try (OutputStream stream = outputOptions.openStream()) {
                JSON.writeTo(stream, ConnectionInfoBuilder.build(databaseManager.getAdapter()),
                        JSONWriter.Feature.PrettyFormatWith2Space);
            } catch (Exception e) {
                throw new ExecutionException("Failed to write connection info as JSON.", e);
            }
        }

        if (!outputOptions.isWriteToStdout()) {
            logger.info("Database details:");
            databaseManager.reportDatabaseInfo(logger::info);
        }

        return CommandLine.ExitCode.OK;
    }
}
