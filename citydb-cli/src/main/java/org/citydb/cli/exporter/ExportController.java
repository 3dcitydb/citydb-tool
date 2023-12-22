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

package org.citydb.cli.exporter;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;
import org.citydb.cli.ExecutionException;
import org.citydb.cli.command.Command;
import org.citydb.cli.option.DatabaseOptions;
import org.citydb.cli.option.OutputFileOptions;
import org.citydb.cli.option.ThreadsOption;
import org.citydb.cli.util.CommandHelper;
import org.citydb.cli.util.QueryExecutor;
import org.citydb.cli.util.QueryResult;
import org.citydb.core.file.OutputFile;
import org.citydb.database.DatabaseManager;
import org.citydb.database.adapter.DatabaseAdapter;
import org.citydb.io.IOAdapter;
import org.citydb.io.IOAdapterManager;
import org.citydb.io.OutputFileBuilder;
import org.citydb.io.writer.FeatureWriter;
import org.citydb.io.writer.WriteOptions;
import org.citydb.io.writer.options.SpatialReference;
import org.citydb.logging.LoggerManager;
import org.citydb.model.feature.Feature;
import org.citydb.operation.exporter.ExportOptions;
import org.citydb.operation.exporter.Exporter;
import org.citydb.operation.util.FeatureStatistics;
import picocli.CommandLine;

import java.util.concurrent.atomic.AtomicLong;

public abstract class ExportController implements Command {
    @CommandLine.Mixin
    protected OutputFileOptions outputFileOptions;

    @CommandLine.Option(names = "--fail-fast",
            description = "Fail fast on errors.")
    protected boolean failFast;

    @CommandLine.Mixin
    protected ThreadsOption threadsOption;

    @CommandLine.Option(names = {"-q", "--query"}, paramLabel = "<select>",
            description = "SQL select statement to use as filter query.")
    private String query;

    @CommandLine.ArgGroup(exclusive = false, multiplicity = "1", order = Integer.MAX_VALUE,
            heading = "Database connection options:%n")
    protected DatabaseOptions databaseOptions;

    protected final Logger logger = LoggerManager.getInstance().getLogger(ExportController.class);
    protected final CommandHelper helper = CommandHelper.newInstance();
    private final Object lock = new Object();
    private volatile boolean shouldRun = true;

    protected abstract IOAdapter getIOAdapter(IOAdapterManager ioManager) throws ExecutionException;
    protected abstract Object getFormatOptions() throws ExecutionException;
    protected void initialize(DatabaseManager databaseManager) throws ExecutionException {}

    @Override
    public Integer call() throws ExecutionException {
        return doExport() ?
                CommandLine.ExitCode.OK :
                CommandLine.ExitCode.SOFTWARE;
    }

    protected boolean doExport() throws ExecutionException {
        IOAdapterManager ioManager = helper.createIOAdapterManager();
        IOAdapter ioAdapter = getIOAdapter(ioManager);
        OutputFileBuilder builder = OutputFileBuilder.newInstance()
                .defaultFileExtension(ioManager.getFileExtensions(ioAdapter).stream()
                        .findFirst()
                        .orElse(null));

        DatabaseManager databaseManager = helper.connect(databaseOptions);
        QueryExecutor executor = QueryExecutor.of(databaseManager.getAdapter());
        FeatureStatistics statistics = helper.createFeatureStatistics(databaseManager.getAdapter());

        helper.logIndexStatus(Level.INFO, databaseManager.getAdapter());
        initialize(databaseManager);

        try (OutputFile outputFile = builder.newOutputFile(outputFileOptions.getFile());
             FeatureWriter writer = ioAdapter.createWriter()) {
            Exporter exporter = Exporter.newInstance();
            ExportOptions exportOptions = getExportOptions().setOutputFile(outputFile);
            WriteOptions writeOptions = getWriteOptions(databaseManager.getAdapter())
                    .setFormatOptions(getFormatOptions());
            AtomicLong counter = new AtomicLong();

            logger.info("Exporting to " + ioManager.getFileFormat(ioAdapter) + " file " + outputFile.getFile() + ".");
            writer.initialize(outputFile, writeOptions);

            logger.info("Querying features matching the request...");
            try (QueryResult result = executor.executeQuery(getQuery(databaseManager.getAdapter()))) {
                exporter.startSession(databaseManager.getAdapter(), exportOptions);
                while (shouldRun && result.hasNext()) {
                    long id = result.getId();
                    exporter.exportFeature(id).whenComplete((feature, t) -> {
                        if (feature != null) {
                            try {
                                writer.write(feature).whenComplete((success, e) -> {
                                    if (success == Boolean.TRUE) {
                                        statistics.add(feature);
                                        long count = counter.incrementAndGet();
                                        if (count % 1000 == 0) {
                                            logger.info(count + " features exported.");
                                        }
                                    } else {
                                        abort(feature, id, e);
                                    }
                                });
                            } catch (Throwable e) {
                                abort(feature, id, e);
                            }
                        } else {
                            abort(null, id, t);
                        }
                    });
                }
            } finally {
                exporter.closeSession();
            }
        } catch (Throwable e) {
            logger.warn("Database export aborted due to an error.");
            throw new ExecutionException("A fatal error has occurred during export.", e);
        } finally {
            databaseManager.disconnect();
            if (!statistics.isEmpty()) {
                logger.info("Export summary:");
                statistics.logFeatureSummary(Level.INFO);
            } else {
                logger.info("No features exported.");
            }
        }

        return shouldRun;
    }

    protected String getQuery(DatabaseAdapter adapter) {
        String schema = adapter.getConnectionDetails().getSchema();
        if (query != null) {
            return "select f.id from " + schema + ".feature f " +
                    "where f.id in (" + query + ")" +
                    "and f.termination_date is null";
        } else {
            return "select f.id from " + schema + ".feature f " +
                    "inner join " + schema + ".objectclass o on o.id = f.objectclass_id " +
                    "where o.is_toplevel = 1 and f.termination_date is null";
        }
    }

    protected ExportOptions getExportOptions() {
        return ExportOptions.defaults()
                .setNumberOfThreads(threadsOption.getNumberOfThreads());
    }

    protected WriteOptions getWriteOptions(DatabaseAdapter databaseAdapter) {
        return WriteOptions.defaults()
                .setFailFast(failFast)
                .setNumberOfThreads(threadsOption.getNumberOfThreads())
                .setEncoding(outputFileOptions.getEncoding())
                .setSpatialReference(new SpatialReference()
                        .setSRID(databaseAdapter.getDatabaseMetadata().getSpatialReference().getSRID())
                        .setURI(databaseAdapter.getDatabaseMetadata().getSpatialReference().getURI()));
    }

    private void abort(Feature feature, long id, Throwable e) {
        synchronized (lock) {
            if (shouldRun) {
                shouldRun = false;
                logger.warn("Database export aborted due to an error.");
                helper.logException(feature == null ?
                        "Failed to export feature (ID: " + id + ")." :
                        "Failed to export " + feature.getFeatureType().getLocalName() +
                                feature.getObjectId().map(objectId -> " '" + objectId + "'").orElse("") +
                                " (ID: " + id + ").", e);
            }
        }
    }
}
