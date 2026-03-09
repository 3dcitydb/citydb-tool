/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.cli.index.drop;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;
import org.citydb.cli.ExecutionException;
import org.citydb.cli.index.IndexController;
import org.citydb.cli.logging.LoggerManager;
import org.citydb.database.DatabaseManager;
import org.citydb.database.schema.Index;
import org.citydb.database.util.IndexHelper;
import picocli.CommandLine;

import java.sql.SQLException;

@CommandLine.Command(
        name = "drop",
        description = "Drop indexes on the database tables.")
public class DropIndexCommand extends IndexController {
    private final Logger logger = LoggerManager.getInstance().getLogger(DropIndexCommand.class);

    @Override
    public Integer call() throws ExecutionException {
        DatabaseManager databaseManager = helper.connect(connectionOptions, config);
        IndexHelper indexHelper = databaseManager.getAdapter().getSchemaAdapter().getIndexHelper();

        logger.info("Dropping database indexes.");

        int i = 1, size = IndexHelper.DEFAULT_INDEXES.size();
        for (Index index : IndexHelper.DEFAULT_INDEXES) {
            try {
                logger.info("[{}|{}] Dropping database index on {}.", i++, size, index);
                indexHelper.drop(index);
            } catch (SQLException e) {
                throw new ExecutionException("Failed to drop database indexes.", e);
            }
        }

        helper.logIndexStatus(Level.INFO, databaseManager.getAdapter());
        return CommandLine.ExitCode.OK;
    }
}
