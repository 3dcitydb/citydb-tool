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

import org.apache.logging.log4j.Logger;
import org.citydb.cli.ExecutionException;
import org.citydb.database.DatabaseManager;
import org.citydb.database.schema.Index;
import org.citydb.database.schema.IndexHelper;
import org.citydb.logging.LoggerManager;
import picocli.CommandLine;

import java.sql.SQLException;
import java.util.List;

@CommandLine.Command(
        name = "create",
        description = "Create indexes on the database tables.")
public class CreateIndexCommand extends IndexController {
    private final Logger logger = LoggerManager.getInstance().getLogger(CreateIndexCommand.class);

    @Override
    public Integer call() throws ExecutionException {
        DatabaseManager databaseManager = helper.connect(connectionOptions, config);
        IndexHelper indexHelper = databaseManager.getAdapter().getSchemaAdapter().getIndexHelper();

        logger.info("Creating database indexes.");
        logger.info("Depending on the database size, this operation may take some time.");

        List<Index> indexes = IndexHelper.DEFAULT_INDEXES;
        for (int i = 0; i < indexes.size(); i++) {
            try {
                Index index = indexes.get(i);
                logger.info("[{}|{}] Creating database index on {}.", i + 1, indexes.size(), index);
                indexHelper.create(index);
            } catch (SQLException e) {
                throw new ExecutionException("Failed to create database indexes.", e);
            }
        }

        return CommandLine.ExitCode.OK;
    }
}
