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

package org.citydb.cli.deleter;

import org.apache.logging.log4j.Logger;
import org.citydb.cli.ExecutionException;
import org.citydb.cli.command.Command;
import org.citydb.cli.option.ConfigOption;
import org.citydb.cli.option.ConnectionOptions;
import org.citydb.cli.option.IndexOption;
import org.citydb.cli.option.MetadataOptions;
import org.citydb.cli.util.CommandHelper;
import org.citydb.cli.util.QueryExecutor;
import org.citydb.cli.util.QueryResult;
import org.citydb.config.Config;
import org.citydb.database.DatabaseManager;
import org.citydb.database.adapter.DatabaseAdapter;
import org.citydb.logging.LoggerManager;
import org.citydb.operation.deleter.DeleteOptions;
import org.citydb.operation.deleter.Deleter;
import org.citydb.operation.deleter.options.DeleteMode;
import org.citydb.operation.util.FeatureStatistics;
import picocli.CommandLine;

import java.util.concurrent.atomic.AtomicLong;

@CommandLine.Command(
        name = "delete",
        description = "Delete features from the database.")
public class DeleteCommand implements Command {
    enum Mode {delete, terminate}

    @CommandLine.Option(names = {"-m", "--delete-mode"}, paramLabel = "<mode>", defaultValue = "delete",
            description = "Delete mode: ${COMPLETION-CANDIDATES} (default: ${DEFAULT-VALUE}).")
    private Mode mode;

    @CommandLine.Mixin
    protected IndexOption indexOption;

    @CommandLine.Option(names = "--preview",
            description = "Run in preview mode. Features will not be deleted.")
    private boolean preview;

    @CommandLine.Option(names = {"-q", "--query"}, paramLabel = "<select>",
            description = "SQL select statement to use as filter query.")
    private String query;

    @CommandLine.ArgGroup(exclusive = false,
            heading = "Metadata options for terminate operations:%n")
    private MetadataOptions metadataOptions;

    @CommandLine.ArgGroup(exclusive = false,
            heading = "Database connection options:%n")
    private ConnectionOptions connectionOptions;

    @CommandLine.Spec
    private CommandLine.Model.CommandSpec commandSpec;

    @ConfigOption
    private Config config;

    private final Logger logger = LoggerManager.getInstance().getLogger();
    private final CommandHelper helper = CommandHelper.of(logger);
    private final Object lock = new Object();
    private volatile boolean shouldRun = true;

    @Override
    public Integer call() throws ExecutionException {
        DatabaseManager databaseManager = helper.connect(connectionOptions, config);
        QueryExecutor executor = QueryExecutor.of(databaseManager.getAdapter());
        Deleter deleter = Deleter.newInstance();

        FeatureStatistics statistics = helper.createFeatureStatistics(databaseManager.getAdapter());
        IndexOption.Mode indexMode = indexOption.getMode();
        AtomicLong counter = new AtomicLong();

        if (indexMode != IndexOption.Mode.keep) {
            logger.info("Dropping database indexes...");
            helper.dropIndexes(databaseManager.getAdapter());
        }

        helper.printIndexStatus(databaseManager.getAdapter(), logger::info);

        if (preview) {
            logger.info("Delete is running in preview mode. Features will not be deleted.");
        }

        try  {
            logger.info("Querying features matching the request...");
            try (QueryResult result = executor.executeQuery(getQuery(databaseManager.getAdapter()))) {
                deleter.startSession(databaseManager.getAdapter(), getDeleteOptions());
                while (shouldRun && result.hasNext()) {
                    long id = result.getId();
                    int objectClassId = result.getObjectClassId();

                    deleter.deleteFeature(id).whenComplete((success, t) -> {
                        if (success == Boolean.TRUE) {
                            statistics.add(objectClassId);
                            long count = counter.incrementAndGet();
                            if (count % 1000 == 0) {
                                logger.info(count + " features processed.");
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

            if (shouldRun && indexMode == IndexOption.Mode.drop_create) {
                logger.info("Re-creating database indexes. This operation may take some time...");
                helper.createIndexes(databaseManager.getAdapter());
            }
        } catch (Throwable e) {
            logger.warn("Database delete aborted due to an error.");
            throw new ExecutionException("A fatal error has occurred during delete.", e);
        } finally {
            databaseManager.disconnect();
            if ((deleter.getState() == Deleter.State.SESSION_COMMITTED || preview)
                    && !statistics.isEmpty()) {
                logger.info(!preview ? "Delete summary:" : "Preview of features to be deleted:");
                statistics.printFeatureSummary(logger::info);
            } else {
                logger.info("No features deleted.");
            }
        }

        return shouldRun ?
                CommandLine.ExitCode.OK :
                CommandLine.ExitCode.SOFTWARE;
    }

    private String getQuery(DatabaseAdapter adapter) {
        String schema = adapter.getConnectionDetails().getSchema();
        String select;
        if (query != null) {
            select = "select f.id, f.objectclass_id, f.objectid from " + schema + ".feature f " +
                    "where f.id in (" + query + ")";
        } else {
            select = "select f.id, f.objectclass_id, f.objectid from " + schema + ".feature f " +
                    "inner join " + schema + ".objectclass o on o.id = f.objectclass_id " +
                    "where o.is_toplevel = 1";
        }

        if (mode == Mode.terminate) {
            select += " and f.termination_date is null";
        }

        return select;
    }

    private DeleteOptions getDeleteOptions() {
        DeleteOptions deleteOptions = config.get(DeleteOptions.class);
        if (deleteOptions != null) {
            if (Command.hasMatchedOption("--delete-mode", commandSpec)) {
                deleteOptions.setMode(mode == Mode.terminate ? DeleteMode.TERMINATE : DeleteMode.DELETE);
            }
        } else {
            deleteOptions = new DeleteOptions()
                    .setMode(mode == Mode.terminate ? DeleteMode.TERMINATE : DeleteMode.DELETE);
        }

        if (metadataOptions != null) {
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

        return deleteOptions;
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
