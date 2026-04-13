/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.cli.visExporter;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;
import org.citydb.cli.ExecutionException;
import org.citydb.cli.common.Command;
import org.citydb.cli.common.ConfigOption;
import org.citydb.cli.common.ConnectionOptions;
import org.citydb.cli.common.ThreadsOptions;
import org.citydb.cli.common.ValidityOptions;
import org.citydb.cli.logging.LoggerManager;
import org.citydb.cli.util.CommandHelper;
import org.citydb.cli.util.FeatureStatistics;
import org.citydb.cli.visExporter.options.QueryOptions;
import org.citydb.config.Config;
import org.citydb.config.ConfigException;
import org.citydb.config.common.ConfigObject;
import org.citydb.config.common.SrsReference;
import org.citydb.core.file.OutputFile;
import org.citydb.database.DatabaseManager;
import org.citydb.database.adapter.DatabaseAdapter;
import org.citydb.io.IOAdapter;
import org.citydb.io.IOAdapterManager;
import org.citydb.io.OutputFileBuilder;
import org.citydb.io.writer.FeatureWriter;
import org.citydb.io.writer.WriteOptions;
import org.citydb.io.writer.options.OutputFormatOptions;
import org.citydb.model.feature.Feature;
import org.citydb.operation.exporter.Exporter;
import org.citydb.operation.exporter.options.AppearanceOptions;
import org.citydb.query.Query;
import org.citydb.query.builder.sql.SqlBuildOptions;
import org.citydb.query.executor.QueryExecutor;
import org.citydb.query.executor.QueryResult;
import org.citydb.query.filter.encoding.FilterParseException;
import picocli.CommandLine;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicLong;

public abstract class VisExportController implements Command {
    @CommandLine.Option(names = {"-o", "--output"}, required = true, paramLabel = "<file>",
            description = "Name of the output file.")
    private Path outputFile;

    @CommandLine.Option(names = "--fail-fast",
            description = "Fail fast on errors.")
    private Boolean failFast;

    @CommandLine.Option(names = "--temp-dir", paramLabel = "<dir>",
            description = "Store temporary files in this directory.")
    private Path tempDirectory;

    @CommandLine.ArgGroup(exclusive = false)
    private ThreadsOptions threadsOptions;

    @CommandLine.ArgGroup(exclusive = false, order = Integer.MAX_VALUE,
            heading = "Query and filter options:%n")
    private QueryOptions queryOptions;

    @CommandLine.ArgGroup(exclusive = false, order = Integer.MAX_VALUE,
            heading = "Time-based feature history options:%n")
    private ValidityOptions validityOptions;

    @CommandLine.ArgGroup(exclusive = false, order = Integer.MAX_VALUE,
            heading = "Database connection options:%n")
    private ConnectionOptions connectionOptions;

    @ConfigOption
    private Config config;

    protected final Logger logger = LoggerManager.getInstance().getLogger(VisExportController.class);
    protected final CommandHelper helper = CommandHelper.getInstance();
    private final Object lock = new Object();
    private volatile boolean shouldRun = true;

    protected abstract IOAdapter getIOAdapter(IOAdapterManager ioManager) throws ExecutionException;

    protected abstract OutputFormatOptions getFormatOptions(ConfigObject<OutputFormatOptions> formatOptions)
            throws ExecutionException;

    protected void initialize(VisExportOptions exportOptions, WriteOptions writeOptions,
                              DatabaseManager databaseManager) throws ExecutionException {
    }

    @Override
    public Integer call() throws ExecutionException {
        return doExport() ?
                CommandLine.ExitCode.OK :
                CommandLine.ExitCode.SOFTWARE;
    }

    protected boolean doExport() throws ExecutionException {
        IOAdapterManager ioManager = helper.getIOAdapterManager();
        IOAdapter ioAdapter = getIOAdapter(ioManager);
        OutputFileBuilder builder = OutputFileBuilder.newInstance()
                .tempDirectory(helper.resolveAgainstWorkingDir(tempDirectory))
                .defaultFileExtension(ioManager.getFileExtensions(ioAdapter).stream()
                        .findFirst()
                        .orElse(null));

        DatabaseManager databaseManager = helper.connect(connectionOptions, config);
        VisExportOptions exportOptions = getExportOptions();
        WriteOptions writeOptions = getWriteOptions(exportOptions, databaseManager.getAdapter());
        writeOptions.getFormatOptions().set(getFormatOptions(writeOptions.getFormatOptions()));

        helper.logIndexStatus(Level.INFO, databaseManager.getAdapter());
        initialize(exportOptions, writeOptions, databaseManager);

        Query query = getQuery(exportOptions);
        FeatureStatistics statistics = new FeatureStatistics(databaseManager.getAdapter());
        AtomicLong counter = new AtomicLong();

        Path file = helper.resolveAgainstWorkingDir(outputFile);
        try (OutputFile output = builder.newOutputFile(file);
             FeatureWriter writer = ioAdapter.createWriter(output, writeOptions)) {
            Exporter exporter = Exporter.newInstance();
            exportOptions.setOutputFile(output);

            Path outputPath = output.getFile();
            String name = outputPath.getFileName().toString();
            int dot = name.lastIndexOf('.');
            logger.info("Exporting to {} file {}.",
                    ioManager.getFileFormat(ioAdapter),
                    dot > 0 ? outputPath.resolveSibling(name.substring(0, dot)) : outputPath);

            QueryExecutor executor = helper.getQueryExecutor(query,
                    SqlBuildOptions.defaults().omitDistinct(true),
                    helper.resolveAgainstWorkingDir(tempDirectory),
                    databaseManager.getAdapter());

            logger.debug("Querying features matching the request...");
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
            if (!statistics.isEmpty()) {
                logger.info("Export summary:");
                statistics.logFeatureSummary(Level.INFO);
            } else {
                logger.info("No features exported.");
            }
        }

        return shouldRun;
    }

    protected VisExportOptions getExportOptions() throws ExecutionException {
        VisExportOptions exportOptions;
        try {
            exportOptions = config.getOrElse(VisExportOptions.class, VisExportOptions::new);
        } catch (ConfigException e) {
            throw new ExecutionException("Failed to get vis-export options from config.", e);
        }

        if (threadsOptions != null && threadsOptions.getNumberOfThreads() != null) {
            exportOptions.setNumberOfThreads(threadsOptions.getNumberOfThreads());
        }

        // Visualization formats require WGS84 geographic coordinates.
        exportOptions.setTargetSrs(SrsReference.of(4326));

        if (queryOptions != null) {
            if (queryOptions.getLodOptions() != null) {
                exportOptions.setLodOptions(queryOptions.getLodOptions().getExportLodOptions());
            }

            if (queryOptions.getAppearanceOptions() != null) {
                AppearanceOptions appearanceOptions = exportOptions.getAppearanceOptions()
                        .orElseGet(AppearanceOptions::new);
                appearanceOptions
                        .setExportAppearances(queryOptions.getAppearanceOptions().isProcessAppearances())
                        .setThemes(queryOptions.getAppearanceOptions().getThemes());
                exportOptions.setAppearanceOptions(appearanceOptions);
            }
        }

        if (validityOptions != null) {
            exportOptions.setValidityOptions(validityOptions.getExportValidityOptions());
        }

        return exportOptions;
    }

    protected Query getQuery(VisExportOptions exportOptions) throws ExecutionException {
        try {
            Query query = queryOptions != null && queryOptions.getQuery() != null ?
                    queryOptions.getQuery() :
                    exportOptions.getQuery().orElseGet(Query::new);

            return helper.setValidityFilter(query, exportOptions.getValidityOptions().orElse(null));
        } catch (FilterParseException e) {
            throw new ExecutionException("Failed to parse the provided CQL2 filter expression.", e);
        }
    }

    protected WriteOptions getWriteOptions(VisExportOptions exportOptions,
                                           DatabaseAdapter adapter) throws ExecutionException {
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
            writeOptions.setTempDirectory(helper.resolveAgainstWorkingDir(tempDirectory));
        }

        if (threadsOptions != null && threadsOptions.getNumberOfThreads() != null) {
            writeOptions.setNumberOfThreads(threadsOptions.getNumberOfThreads());
        }

        if (writeOptions.getSrsName().isEmpty()) {
            writeOptions.setSrsName(adapter.getGeometryAdapter().getSrsHelper()
                    .getDefaultIdentifier(4326));
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
