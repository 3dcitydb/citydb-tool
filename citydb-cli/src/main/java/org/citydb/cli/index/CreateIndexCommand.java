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

package org.citydb.cli.index;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;
import org.citydb.cli.ExecutionException;
import org.citydb.cli.logging.LoggerManager;
import org.citydb.database.DatabaseManager;
import org.citydb.database.schema.Index;
import org.citydb.database.util.IndexHelper;
import picocli.CommandLine;

import java.sql.SQLException;

@CommandLine.Command(
        name = "create",
        description = "Create indexes on the database tables.")
public class CreateIndexCommand extends IndexController {
    enum Mode {partial, full}

    @CommandLine.Option(names = {"-m", "--index-mode"}, paramLabel = "<mode>", defaultValue = "partial",
            description = "Index mode for property value columns: ${COMPLETION-CANDIDATES} " +
                    "(default: ${DEFAULT-VALUE}). Null values are not indexed in partial mode.")
    private Mode mode;

    private final Logger logger = LoggerManager.getInstance().getLogger(CreateIndexCommand.class);

    @Override
    public Integer call() throws ExecutionException {
        DatabaseManager databaseManager = helper.connect(connectionOptions, config);
        IndexHelper indexHelper = databaseManager.getAdapter().getSchemaAdapter().getIndexHelper();

        logger.info("Creating database indexes.");
        logger.info("Depending on the database size, this operation may take some time.");

        int i = 1, size = IndexHelper.DEFAULT_INDEXES.size();
        for (Index index : IndexHelper.DEFAULT_INDEXES) {
            try {
                logger.info("[{}|{}] Creating database index on {}.", i++, size, index);
                indexHelper.create(index, mode == Mode.partial && IndexHelper.DEFAULT_PARTIAL_INDEXES.contains(index));
            } catch (SQLException e) {
                throw new ExecutionException("Failed to create database indexes.", e);
            }
        }

        helper.logIndexStatus(Level.INFO, databaseManager.getAdapter());
        return CommandLine.ExitCode.OK;
    }
}
