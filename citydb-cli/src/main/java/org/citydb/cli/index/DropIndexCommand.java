/*
 * citydb-tool - Command-line tool for the 3D City Database
 * https://www.3dcitydb.org/
 *
 * Copyright 2022-2023
 * Virtual City Systems, Germany
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

import org.citydb.cli.ExecutionException;
import org.citydb.database.DatabaseManager;
import org.citydb.database.schema.Index;
import org.citydb.database.schema.IndexHelper;
import picocli.CommandLine;

import java.sql.SQLException;
import java.util.List;

@CommandLine.Command(
        name = "drop",
        description = "Drop indexes on the database tables.")
public class DropIndexCommand extends IndexController {

    @Override
    public Integer call() throws ExecutionException {
        DatabaseManager databaseManager = helper.connect(databaseOptions);
        IndexHelper indexHelper = databaseManager.getAdapter().getSchemaAdapter().getIndexHelper();

        logger.info("Dropping database indexes.");

        List<Index> indexes = IndexHelper.DEFAULT_INDEXES;
        for (int i = 0; i < indexes.size(); i++) {
            try {
                Index index = indexes.get(i);
                logger.info("[" + (i + 1) + "|" + indexes.size() + "] Dropping database index on " + index + ".");
                indexHelper.drop(index);
            } catch (SQLException e) {
                throw new ExecutionException("Failed to drop database indexes.", e);
            }
        }

        return CommandLine.ExitCode.OK;
    }
}
