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

package org.citydb.cli.exporter;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;
import org.citydb.cli.ExecutionException;
import org.citydb.cli.common.*;
import org.citydb.cli.exporter.options.QueryOptions;
import org.citydb.cli.exporter.util.SequentialWriter;
import org.citydb.cli.util.CommandHelper;
import org.citydb.config.Config;
import org.citydb.config.ConfigException;
import org.citydb.config.common.ConfigObject;
import org.citydb.config.common.SrsReference;
import org.citydb.core.file.OutputFile;
import org.citydb.database.DatabaseManager;
import org.citydb.database.adapter.DatabaseAdapter;
import org.citydb.database.util.SrsHelper;
import org.citydb.io.IOAdapter;
import org.citydb.io.IOAdapterManager;
import org.citydb.io.OutputFileBuilder;
import org.citydb.io.writer.FeatureWriter;
import org.citydb.io.writer.WriteException;
import org.citydb.io.writer.WriteOptions;
import org.citydb.io.writer.options.OutputFormatOptions;
import org.citydb.logging.LoggerManager;
import org.citydb.model.feature.Feature;
import org.citydb.operation.exporter.Exporter;
import org.citydb.operation.util.FeatureStatistics;
import org.citydb.query.Query;
import org.citydb.query.executor.QueryExecutor;
import org.citydb.query.executor.QueryResult;
import org.citydb.query.filter.encoding.FilterParseException;
import org.citydb.query.util.QueryHelper;
import picocli.CommandLine;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicLong;

public abstract class ExportController implements Command {
    @CommandLine.Mixin
    protected OutputFileOptions outputFileOptions;

    @CommandLine.Option(names = "--fail-fast",
            description = "Fail fast on errors.")
    protected Boolean failFast;

    @CommandLine.Option(names = "--temp-dir", paramLabel = "<dir>",
            description = "Store temporary files in this directory.")
    protected Path tempDirectory;

    @CommandLine.Mixin
    protected ThreadsOption threadsOption;

    @CommandLine.Mixin
    protected CrsOptions crsOptions;

    @CommandLine.ArgGroup(exclusive = false, order = Integer.MAX_VALUE,
            heading = "Query and filter options:%n")
    private QueryOptions queryOptions;

    @CommandLine.ArgGroup(exclusive = false, order = Integer.MAX_VALUE,
            heading = "Database connection options:%n")
    protected ConnectionOptions connectionOptions;

    @ConfigOption
    private Config config;

    protected final Logger logger = LoggerManager.getInstance().getLogger(ExportController.class);
    protected final CommandHelper helper = CommandHelper.newInstance();
    private final Object lock = new Object();
    private volatile boolean shouldRun = true;

    protected abstract IOAdapter getIOAdapter(IOAdapterManager ioManager) throws ExecutionException;

    protected abstract OutputFormatOptions getFormatOptions(ConfigObject<OutputFormatOptions> formatOptions) throws ExecutionException;

    protected void initialize(ExportOptions exportOptions, WriteOptions writeOptions, DatabaseManager databaseManager) throws ExecutionException {
    }

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
                .tempDirectory(helper.resolveDirectory(tempDirectory))
                .defaultFileExtension(ioManager.getFileExtensions(ioAdapter).stream()
                        .findFirst()
                        .orElse(null));

        DatabaseManager databaseManager = helper.connect(connectionOptions, config);
        ExportOptions exportOptions = getExportOptions();
        WriteOptions writeOptions = getWriteOptions(exportOptions, databaseManager.getAdapter());
        writeOptions.getFormatOptions().set(getFormatOptions(writeOptions.getFormatOptions()));

        Query query = getQuery(exportOptions);
        QueryExecutor executor = helper.getQueryExecutor(query, databaseManager.getAdapter());

        FeatureStatistics statistics = new FeatureStatistics(databaseManager.getAdapter());
        helper.logIndexStatus(Level.INFO, databaseManager.getAdapter());
        initialize(exportOptions, writeOptions, databaseManager);

        try (OutputFile outputFile = builder.newOutputFile(outputFileOptions.getFile());
             FeatureWriter writer = createWriter(query, ioAdapter)) {
            Exporter exporter = Exporter.newInstance();
            exportOptions.setOutputFile(outputFile);

            AtomicLong counter = new AtomicLong();

            logger.info("Exporting to {} file {}.", ioManager.getFileFormat(ioAdapter), outputFile.getFile());
            writer.initialize(outputFile, writeOptions);

            logger.info("Querying features matching the request...");
            logger.trace("Using SQL query:\n{}", () -> helper.getFormattedSql(executor.getSelect(),
                    databaseManager.getAdapter()));

            long sequenceId = 1;
            try (QueryResult result = executor.executeQuery()) {
                exporter.startSession(databaseManager.getAdapter(), exportOptions);
                while (shouldRun && result.hasNext()) {
                    long id = result.getId();
                    exporter.exportFeature(id, sequenceId++).whenComplete((feature, t) -> {
                        if (feature != null) {
                            try {
                                writer.write(feature, (success, e) -> {
                                    if (success == Boolean.TRUE) {
                                        statistics.add(feature);
                                        long count = counter.incrementAndGet();
                                        if (count % 1000 == 0) {
                                            logger.info("{} features exported.", count);
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

    private FeatureWriter createWriter(Query query, IOAdapter ioAdapter) throws WriteException {
        FeatureWriter writer = ioAdapter.createWriter();
        return query.getSorting().isPresent() ?
                SequentialWriter.of(writer) :
                writer;
    }

    protected Query getQuery(ExportOptions exportOptions) throws ExecutionException {
        try {
            return queryOptions != null ?
                    queryOptions.getQuery() :
                    exportOptions.getQuery().orElseGet(QueryHelper::getNonTerminatedTopLevelFeatures);
        } catch (FilterParseException e) {
            throw new ExecutionException("Failed to parse the provided CQL2 filter expression.", e);
        }
    }

    protected ExportOptions getExportOptions() throws ExecutionException {
        ExportOptions exportOptions;
        try {
            exportOptions = config.getOrElse(ExportOptions.class, ExportOptions::new);
        } catch (ConfigException e) {
            throw new ExecutionException("Failed to get export options from config.", e);
        }

        if (threadsOption.getNumberOfThreads() != null) {
            exportOptions.setNumberOfThreads(threadsOption.getNumberOfThreads());
        }

        if (crsOptions.getTargetSrs() != null) {
            exportOptions.setTargetSrs(crsOptions.getTargetSrs());
        }

        if (queryOptions != null && queryOptions.getLodOptions() != null) {
            exportOptions.setLodOptions(queryOptions.getLodOptions().getLodExportOptions());
        }

        return exportOptions;
    }

    protected WriteOptions getWriteOptions(ExportOptions exportOptions, DatabaseAdapter adapter) throws ExecutionException {
        WriteOptions writeOptions;
        try {
            writeOptions = config.getOrElse(WriteOptions.class, WriteOptions::new);
        } catch (ConfigException e) {
            throw new ExecutionException("Failed to get write options from config.", e);
        }

        if (failFast != null) {
            writeOptions.setFailFast(failFast);
        }

        if (tempDirectory != null) {
            writeOptions.setTempDirectory(tempDirectory.toString());
        }

        if (threadsOption.getNumberOfThreads() != null) {
            writeOptions.setNumberOfThreads(threadsOption.getNumberOfThreads());
        }

        if (outputFileOptions.getEncoding() != null) {
            writeOptions.setEncoding(outputFileOptions.getEncoding());
        }

        if (crsOptions.getName() != null) {
            writeOptions.setSrsName(crsOptions.getName());
        } else if (writeOptions.getSrsName().isEmpty()) {
            SrsReference targetSrs = exportOptions.getTargetSrs().orElse(null);
            if (targetSrs != null && targetSrs.getIdentifier().isPresent()) {
                writeOptions.setSrsName(targetSrs.getIdentifier().get());
            } else if (targetSrs != null && targetSrs.getSRID().isPresent()) {
                writeOptions.setSrsName(SrsHelper.getInstance().getDefaultIdentifier(targetSrs.getSRID().get()));
            } else {
                writeOptions.setSrsName(adapter.getDatabaseMetadata().getSpatialReference().getIdentifier());
            }
        }

        return writeOptions;
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
