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
import org.citydb.cli.exporter.options.TilingOptions;
import org.citydb.cli.exporter.util.SequentialWriter;
import org.citydb.cli.exporter.util.TilingHelper;
import org.citydb.cli.util.CommandHelper;
import org.citydb.cli.util.FeatureStatistics;
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
import org.citydb.io.writer.WriteException;
import org.citydb.io.writer.WriteOptions;
import org.citydb.io.writer.options.OutputFormatOptions;
import org.citydb.logging.LoggerManager;
import org.citydb.model.feature.Feature;
import org.citydb.operation.exporter.Exporter;
import org.citydb.operation.exporter.options.AppearanceOptions;
import org.citydb.query.Query;
import org.citydb.query.QueryHelper;
import org.citydb.query.builder.sql.SqlBuildOptions;
import org.citydb.query.executor.QueryExecutor;
import org.citydb.query.executor.QueryResult;
import org.citydb.query.filter.encoding.FilterParseException;
import org.citydb.util.tiling.Tile;
import org.citydb.util.tiling.TileIterator;
import org.citydb.util.tiling.Tiling;
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
    protected ThreadsOptions threadsOptions;

    @CommandLine.Mixin
    protected CrsOptions crsOptions;

    @CommandLine.Mixin
    protected TransformOptions transformOptions;

    @CommandLine.ArgGroup(exclusive = false, order = Integer.MAX_VALUE,
            heading = "Query and filter options:%n")
    protected QueryOptions queryOptions;

    @CommandLine.ArgGroup(exclusive = false, order = Integer.MAX_VALUE,
            heading = "Tiling options:%n")
    protected TilingOptions tilingOptions;

    @CommandLine.ArgGroup(exclusive = false, order = Integer.MAX_VALUE,
            heading = "Database connection options:%n")
    protected ConnectionOptions connectionOptions;

    @ConfigOption
    private Config config;

    protected static final int ARG_GROUP_ORDER = 1;
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

        helper.logIndexStatus(Level.INFO, databaseManager.getAdapter());
        initialize(exportOptions, writeOptions, databaseManager);

        Query query = getQuery(exportOptions);
        Tiling tiling = getTiling(exportOptions);
        FeatureStatistics statistics = new FeatureStatistics(databaseManager.getAdapter());
        AtomicLong counter = new AtomicLong();

        try {
            TilingHelper tilingHelper = TilingHelper.of(tiling, query, databaseManager.getAdapter());
            if (tilingHelper.isUseTiling()) {
                logger.info("Creating {} tile(s) based on provided tiling scheme.",
                        tilingHelper.getTileMatrix().size());
            }

            TileIterator iterator = tilingHelper.getTileMatrix().getTileIterator();
            while (iterator.hasNext()) {
                Tile tile = iterator.next();
                QueryExecutor executor = helper.getQueryExecutor(tilingHelper.getTileQuery(tile),
                        SqlBuildOptions.defaults()
                                .omitDistinct(true)
                                .withColumn(tilingHelper.isUseTiling() ? "envelope" : null),
                        tempDirectory,
                        databaseManager.getAdapter());

                Path file = tilingHelper.getOutputFile(outputFileOptions.getFile(), tile);
                FeatureStatistics tileStatistics = new FeatureStatistics(databaseManager.getAdapter());

                try (OutputFile outputFile = builder.newOutputFile(file);
                     FeatureWriter writer = createWriter(outputFile, writeOptions, query, ioAdapter)) {
                    Exporter exporter = Exporter.newInstance();
                    exportOptions.setOutputFile(outputFile);

                    logger.info("{}Exporting to {} file {}.", getTileCounter(tilingHelper, tile),
                            ioManager.getFileFormat(ioAdapter), outputFile.getFile());

                    logger.debug("Querying features matching the request...");
                    logger.trace("Using SQL query:\n{}", () -> helper.getFormattedSql(executor.getSelect(),
                            databaseManager.getAdapter()));

                    long sequenceId = 1;
                    try (QueryResult result = executor.executeQuery()) {
                        exporter.startSession(databaseManager.getAdapter(), exportOptions);
                        while (shouldRun && result.hasNext()) {
                            long id = result.getId();

                            if (tilingHelper.isUseTiling() && !tile.isOnTile(databaseManager.getAdapter()
                                    .getGeometryAdapter()
                                    .getEnvelope(result.get(rs -> rs.getObject("envelope"))))) {
                                continue;
                            }

                            exporter.exportFeature(id, sequenceId++).whenComplete((feature, t) -> {
                                if (feature != null) {
                                    try {
                                        writer.write(feature, (success, e) -> {
                                            if (success == Boolean.TRUE) {
                                                tileStatistics.add(feature);
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
                    statistics.merge(tileStatistics);
                    if (tilingHelper.isUseTiling()) {
                        logStatistics(tileStatistics, "Tile export summary:", Level.DEBUG);
                    }
                }
            }
        } finally {
            databaseManager.disconnect();
            logStatistics(statistics, "Export summary:", Level.INFO);
        }

        return shouldRun;
    }

    private FeatureWriter createWriter(OutputFile file, WriteOptions options, Query query, IOAdapter ioAdapter) throws WriteException {
        FeatureWriter writer = ioAdapter.createWriter(file, options);
        return query.getSorting().isPresent() ?
                SequentialWriter.of(writer) :
                writer;
    }

    protected Query getQuery(ExportOptions exportOptions) throws ExecutionException {
        try {
            return queryOptions != null ?
                    queryOptions.getQuery() :
                    exportOptions.getQuery().orElseGet(QueryHelper::getActiveTopLevelFeatures);
        } catch (FilterParseException e) {
            throw new ExecutionException("Failed to parse the provided CQL2 filter expression.", e);
        }
    }

    protected Tiling getTiling(ExportOptions exportOptions) throws ExecutionException {
        return tilingOptions != null ?
                tilingOptions.getTiling() :
                exportOptions.getTiling().orElseGet(TilingHelper::noTiling);
    }

    protected ExportOptions getExportOptions() throws ExecutionException {
        ExportOptions exportOptions;
        try {
            exportOptions = config.getOrElse(ExportOptions.class, ExportOptions::new);
        } catch (ConfigException e) {
            throw new ExecutionException("Failed to get export options from config.", e);
        }

        if (threadsOptions.getNumberOfThreads() != null) {
            exportOptions.setNumberOfThreads(threadsOptions.getNumberOfThreads());
        }

        if (crsOptions.getTargetSrs() != null) {
            exportOptions.setTargetSrs(crsOptions.getTargetSrs());
        }

        if (transformOptions != null) {
            exportOptions.setAffineTransform(transformOptions.getTransformationMatrix());
        }

        if (queryOptions != null) {
            if (queryOptions.getLodOptions() != null) {
                exportOptions.setLodOptions(queryOptions.getLodOptions().getExportLodOptions());
            }

            if (queryOptions.getAppearanceOptions() != null) {
                getAppearanceOptions(exportOptions)
                        .setExportAppearances(queryOptions.getAppearanceOptions().isProcessAppearances())
                        .setThemes(queryOptions.getAppearanceOptions().getThemes());
            }
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

        if (threadsOptions.getNumberOfThreads() != null) {
            writeOptions.setNumberOfThreads(threadsOptions.getNumberOfThreads());
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
                writeOptions.setSrsName(adapter.getGeometryAdapter().getSrsHelper()
                        .getDefaultIdentifier(targetSrs.getSRID().get()));
            } else {
                writeOptions.setSrsName(adapter.getDatabaseMetadata().getSpatialReference().getIdentifier());
            }
        }

        return writeOptions;
    }

    private AppearanceOptions getAppearanceOptions(ExportOptions exportOptions) {
        AppearanceOptions appearanceOptions = exportOptions.getAppearanceOptions()
                .orElseGet(AppearanceOptions::new);
        exportOptions.setAppearanceOptions(appearanceOptions);
        return appearanceOptions;
    }

    private String getTileCounter(TilingHelper helper, Tile tile) {
        return helper.isUseTiling() ?
                "[" + (tile.getRow() * helper.getTileMatrix().getColumns() + tile.getColumn() + 1) + "|" +
                        helper.getTileMatrix().size() + "] " :
                "";
    }

    private void logStatistics(FeatureStatistics statistics, String title, Level level) {
        if (!statistics.isEmpty()) {
            logger.log(level, title);
            statistics.logFeatureSummary(level);
        } else {
            logger.log(level, "No features exported.");
        }
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
