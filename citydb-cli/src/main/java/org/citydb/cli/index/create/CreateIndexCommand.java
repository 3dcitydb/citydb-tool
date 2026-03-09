/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.cli.index.create;

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
