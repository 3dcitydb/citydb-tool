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

package org.citydb.cli.deleter;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;
import org.citydb.cli.ExecutionException;
import org.citydb.cli.common.*;
import org.citydb.cli.deleter.options.MetadataOptions;
import org.citydb.cli.deleter.options.QueryOptions;
import org.citydb.cli.logging.LoggerManager;
import org.citydb.cli.util.CommandHelper;
import org.citydb.config.Config;
import org.citydb.config.ConfigException;
import org.citydb.database.DatabaseManager;
import org.citydb.operation.deleter.Deleter;
import org.citydb.operation.deleter.options.DeleteMode;
import org.citydb.query.Query;
import org.citydb.query.builder.sql.SqlBuildOptions;
import org.citydb.query.executor.QueryExecutor;
import org.citydb.query.executor.QueryResult;
import org.citydb.query.filter.encoding.FilterParseException;
import picocli.CommandLine;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicLong;

@CommandLine.Command(
        name = "delete",
        description = "Delete features from the database.")
public class DeleteCommand implements Command {
    enum Mode {delete, terminate}

    @CommandLine.Option(names = "--temp-dir", paramLabel = "<dir>",
            description = "Store temporary files in this directory.")
    protected Path tempDirectory;

    @CommandLine.Option(names = {"-m", "--delete-mode"}, paramLabel = "<mode>", defaultValue = "terminate",
            description = "Delete mode: ${COMPLETION-CANDIDATES} (default: ${DEFAULT-VALUE}).")
    private Mode mode;

    @CommandLine.Option(names = "--no-terminate-all", negatable = true, defaultValue = "true",
            description = "Also terminate sub-features (default: ${DEFAULT-VALUE}).")
    private boolean terminateAll;

    @CommandLine.Mixin
    protected IndexOptions indexOptions;

    @CommandLine.Option(names = "--preview",
            description = "Run in preview mode. Features will not be deleted.")
    private boolean preview;

    @CommandLine.Option(names = {"-c", "--commit"}, paramLabel = "<number>",
            description = "Commit after deleting this number of features.")
    private Integer commitAfter;

    @CommandLine.ArgGroup(exclusive = false,
            heading = "Metadata options for terminate operations:%n")
    private MetadataOptions metadataOptions;

    @CommandLine.ArgGroup(exclusive = false,
            heading = "Query and filter options:%n")
    private QueryOptions queryOptions;

    @CommandLine.ArgGroup(exclusive = false,
            heading = "Time-based feature history options:%n")
    protected ValidityOptions validityOptions;

    @CommandLine.ArgGroup(exclusive = false,
            heading = "Database connection options:%n")
    private ConnectionOptions connectionOptions;

    @CommandLine.Spec
    private CommandLine.Model.CommandSpec commandSpec;

    @ConfigOption
    private Config config;

    private final Logger logger = LoggerManager.getInstance().getLogger(DeleteCommand.class);
    private final CommandHelper helper = CommandHelper.getInstance();
    private final Object lock = new Object();
    private volatile boolean shouldRun = true;

    @Override
    public Integer call() throws ExecutionException {
        DatabaseManager databaseManager = helper.connect(connectionOptions, config);
        boolean autoCommit = !preview && commitAfter != null;

        DeleteLogger deleteLogger = new DeleteLogger(autoCommit, databaseManager.getAdapter());
        DeleteOptions deleteOptions = getDeleteOptions();
        Deleter deleter = Deleter.newInstance()
                .setDeleteLogger(deleteLogger);

        Query query = getQuery(deleteOptions);
        QueryExecutor executor = helper.getQueryExecutor(query,
                SqlBuildOptions.defaults().omitDistinct(true),
                helper.resolveAgainstWorkingDir(tempDirectory),
                databaseManager.getAdapter());

        IndexOptions.Mode indexMode = indexOptions.getMode();
        AtomicLong counter = new AtomicLong();

        if (indexMode != IndexOptions.Mode.keep) {
            logger.info("Dropping database indexes...");
            helper.dropIndexes(databaseManager.getAdapter());
        }

        helper.logIndexStatus(Level.INFO, databaseManager.getAdapter());
        logger.info("{} features in the database.", mode == Mode.terminate ? "Terminating" : "Deleting");

        if (preview) {
            logger.info("Delete is running in preview mode. Features will not be deleted.");
            deleter.setTransactionMode(Deleter.TransactionMode.AUTO_ROLLBACK);
        } else if (autoCommit) {
            logger.info("Committing delete operation after {} feature(s).", commitAfter);
            deleter.setTransactionMode(Deleter.TransactionMode.AUTO_COMMIT);
            deleteOptions.setBatchSize(commitAfter);
        }

        try {
            logger.debug("Querying features matching the request...");
            logger.trace("Using SQL query:\n{}", () -> helper.getFormattedSql(executor.getSelect(),
                    databaseManager.getAdapter()));

            try (QueryResult result = executor.executeQuery()) {
                deleter.startSession(databaseManager.getAdapter(), deleteOptions);
                while (shouldRun && result.hasNext()) {
                    long id = result.getId();
                    deleteLogger.add(id, result.getObjectClassId());

                    deleter.deleteFeature(id).whenComplete((success, t) -> {
                        if (success == Boolean.TRUE) {
                            long count = counter.incrementAndGet();
                            if (count % 1000 == 0) {
                                logger.info("{} features processed.", count);
                            }
                        } else {
                            abort(id, t);
                        }
                    });
                }
            } finally {
                if (shouldRun && !preview && deleter.wasSuccessful()) {
                    deleter.commitSession();
                } else {
                    deleter.abortSession();
                }
            }

            if (shouldRun && indexMode == IndexOptions.Mode.drop_create) {
                logger.info("Re-creating database indexes. This operation may take some time...");
                helper.createIndexes(databaseManager.getAdapter());
            }
        } catch (Throwable e) {
            logger.warn("Database delete aborted due to an error.");
            throw new ExecutionException("A fatal error has occurred during delete.", e);
        } finally {
            if ((deleter.getState() == Deleter.State.SESSION_COMMITTED || autoCommit || preview)
                    && !deleteLogger.getStatistics().isEmpty()) {
                logger.info(!preview ? "Delete summary:" : "Preview of features to be deleted:");
                deleteLogger.getStatistics().logFeatureSummary(Level.INFO);
            } else {
                logger.info("No features deleted.");
            }
        }

        return shouldRun ?
                CommandLine.ExitCode.OK :
                CommandLine.ExitCode.SOFTWARE;
    }

    private Query getQuery(DeleteOptions deleteOptions) throws ExecutionException {
        try {
            Query query = queryOptions != null ?
                    queryOptions.getQuery() :
                    deleteOptions.getQuery().orElseGet(Query::new);

            return helper.setValidityFilter(query, deleteOptions.getValidityOptions().orElse(null));
        } catch (FilterParseException e) {
            throw new ExecutionException("Failed to parse the provided CQL2 filter expression.", e);
        }
    }

    private DeleteOptions getDeleteOptions() throws ExecutionException {
        DeleteOptions deleteOptions;
        try {
            deleteOptions = config.getOrElse(DeleteOptions.class, DeleteOptions::new);
        } catch (ConfigException e) {
            throw new ExecutionException("Failed to get delete options from config.", e);
        }

        if (Command.hasMatchedOption("--delete-mode", commandSpec)) {
            deleteOptions.setMode(mode == Mode.terminate ? DeleteMode.TERMINATE : DeleteMode.DELETE);
        }

        if (Command.hasMatchedOption("--no-terminate-all", commandSpec)) {
            deleteOptions.setTerminateWithSubFeatures(terminateAll);
        }

        if (metadataOptions != null) {
            if (metadataOptions.getTerminationDate() != null) {
                deleteOptions.setTerminationDate(metadataOptions.getTerminationDate());
            }

            if (metadataOptions.getLineage() != null) {
                deleteOptions.setLineage(metadataOptions.getLineage());
            }

            if (metadataOptions.getUpdatingPerson() != null) {
                deleteOptions.setUpdatingPerson(metadataOptions.getUpdatingPerson());
            }

            if (metadataOptions.getReasonForUpdate() != null) {
                deleteOptions.setReasonForUpdate(metadataOptions.getReasonForUpdate());
            }
        }

        if (validityOptions != null) {
            deleteOptions.setValidityOptions(validityOptions.getExportValidityOptions());
        }

        return deleteOptions;
    }

    @Override
    public void preprocess(CommandLine commandLine) {
        if (commitAfter != null && commitAfter <= 0) {
            throw new CommandLine.ParameterException(commandLine,
                    "Error: The number for --commit must be a positive integer but was '" + commitAfter + "'");
        }
    }

    private void abort(long id, Throwable e) {
        synchronized (lock) {
            if (shouldRun) {
                shouldRun = false;
                logger.warn("Database delete aborted due to an error.");
                helper.logException("Failed to delete feature (ID: " + id + ").", e);
            }
        }
    }
}
