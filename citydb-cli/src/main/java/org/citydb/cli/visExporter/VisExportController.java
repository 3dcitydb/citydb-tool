/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences <https://www.hft-stuttgart.de>
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
import org.citydb.cli.visExporter.options.SceneOptions;
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
import org.citydb.query.builder.QueryBuildException;
import org.citydb.query.builder.sql.SqlBuildOptions;
import org.citydb.query.executor.QueryExecutor;
import org.citydb.query.executor.QueryResult;
import org.citydb.query.filter.encoding.FilterParseException;
import org.citydb.vis.writer.VisFormatOptions;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicLong;

public abstract class VisExportController<T extends VisFormatOptions> implements Command {
    @CommandLine.Option(names = {"-o", "--output"}, required = true, paramLabel = "<file>",
            description = "Name of the output file.")
    protected Path outputFile;

    @CommandLine.Option(names = "--fail-fast",
            description = "Fail fast on errors.")
    protected Boolean failFast;

    @CommandLine.Option(names = "--temp-dir", paramLabel = "<dir>",
            description = "Store temporary files in this directory.")
    protected Path tempDirectory;

    @CommandLine.ArgGroup(exclusive = false)
    protected ThreadsOptions threadsOptions;

    @CommandLine.ArgGroup(exclusive = false, order = Integer.MAX_VALUE,
            heading = "Query and filter options:%n")
    protected QueryOptions queryOptions;

    @CommandLine.ArgGroup(exclusive = false, order = Integer.MAX_VALUE,
            heading = "Time-based feature history options:%n")
    protected ValidityOptions validityOptions;

    @CommandLine.ArgGroup(exclusive = false, order = Integer.MAX_VALUE,
            heading = "Scene options:%n")
    protected SceneOptions sceneOptions;

    @CommandLine.ArgGroup(exclusive = false, order = Integer.MAX_VALUE,
            heading = "Database connection options:%n")
    protected ConnectionOptions connectionOptions;

    @ConfigOption
    private Config config;

    @CommandLine.Spec
    protected CommandLine.Model.CommandSpec commandSpec;

    protected final Logger logger = LoggerManager.getInstance().getLogger(VisExportController.class);
    protected final CommandHelper helper = CommandHelper.getInstance();
    private final Object lock = new Object();
    private volatile boolean shouldRun = true;

    private static final int FEATURES_PER_TEXTURE_BUCKET = 1000;

    protected abstract IOAdapter getIOAdapter(IOAdapterManager ioManager) throws ExecutionException;

    /**
     * Return the config-loaded format options for this writer. Implementations
     * should resolve their concrete {@link VisFormatOptions} subtype via
     * {@link ConfigObject#getOrElse(Class, java.util.function.Supplier)} and
     * wrap any {@link ConfigException} as an {@link ExecutionException}.
     * <p>
     * Called once by the {@link #getFormatOptions} template method; do not
     * apply shared {@link SceneOptions} here — the template runs that step next.
     */
    protected abstract T newFormatOptions(ConfigObject<OutputFormatOptions> formatOptions)
            throws ExecutionException;

    /**
     * Hook for subclasses to apply format-specific CLI options that go beyond
     * the shared {@link SceneOptions}. Invoked by {@link #getFormatOptions}
     * after the shared scene options have been applied, so values set here
     * take precedence over both the config and the scene options.
     */
    protected void applyAdditionalFormatOptions(T options) {
    }

    protected void initialize(VisExportOptions exportOptions, WriteOptions writeOptions,
                              DatabaseManager databaseManager) throws ExecutionException {
    }

    /**
     * Template method assembling the final format options in a fixed order:
     * <ol>
     *   <li>{@link #newFormatOptions} loads the format-specific options from config</li>
     *   <li>Shared {@link SceneOptions} CLI values override matching config entries</li>
     *   <li>{@link #applyAdditionalFormatOptions} applies any format-specific CLI overrides</li>
     * </ol>
     * Declared {@code final} to preserve this ordering. Subclasses customize
     * behavior by implementing {@link #newFormatOptions} and optionally
     * overriding {@link #applyAdditionalFormatOptions}.
     */
    protected final OutputFormatOptions getFormatOptions(ConfigObject<OutputFormatOptions> formatOptions)
            throws ExecutionException {
        T options = newFormatOptions(formatOptions);
        applySceneOptions(options);
        applyAdditionalFormatOptions(options);
        return options;
    }

    /**
     * Apply the shared scene CLI options to the given format options.
     * Only values explicitly matched on the command line override the config.
     */
    private void applySceneOptions(VisFormatOptions options) {
        if (sceneOptions == null) {
            return;
        }

        if (Command.hasMatchedOption("--max-features-per-node", commandSpec)) {
            options.setMaxFeaturesPerNode(sceneOptions.getMaxFeaturesPerNode());
        }

        if (Command.hasMatchedOption("--max-tree-depth", commandSpec)) {
            options.setMaxTreeDepth(sceneOptions.getMaxTreeDepth());
        }

        if (Command.hasMatchedOption("--clamp-to-ground", commandSpec)) {
            options.setClampToGround(sceneOptions.isClampToGround());
        }

        if (Command.hasMatchedOption("--texture-scale", commandSpec)) {
            options.setTextureScale(sceneOptions.getTextureScale());
        }
    }

    private void configureTextureBuckets(VisExportOptions exportOptions, DatabaseManager databaseManager,
                                         IOAdapterManager ioManager, IOAdapter ioAdapter,
                                         Path outputFileParent, Path tempDir) throws ExecutionException {
        if (!exportOptions.getAppearanceOptions()
                .map(AppearanceOptions::isExportAppearances)
                .orElse(true)) {
            return;
        }

        long featureCount;
        try {
            featureCount = QueryExecutor.builder(databaseManager.getAdapter())
                    .build(getQuery(exportOptions))
                    .countHits();
        } catch (QueryBuildException | SQLException | IOException e) {
            throw new ExecutionException("Failed to count features for texture-bucket sizing.", e);
        }

        int buckets = (int) ((featureCount + FEATURES_PER_TEXTURE_BUCKET - 1)
                / FEATURES_PER_TEXTURE_BUCKET);
        logger.debug("Sized texture buckets to {} for {} features.", buckets, featureCount);

        AppearanceOptions appearanceOptions = exportOptions.getAppearanceOptions()
                .orElseGet(AppearanceOptions::new);
        appearanceOptions.setNumberOfTextureBuckets(buckets);

        Path texturesInTemp = tempDir.resolve(
                ioManager.getFileFormat(ioAdapter).toLowerCase() + "-textures");
        String folder = outputFileParent.relativize(texturesInTemp).toString();
        appearanceOptions.setTextureOutputFolder(folder);

        exportOptions.setAppearanceOptions(appearanceOptions);
    }

    @Override
    public Integer call() throws ExecutionException {
        return doExport() ?
                CommandLine.ExitCode.OK :
                CommandLine.ExitCode.SOFTWARE;
    }

    /**
     * Orchestrates the visualization export pipeline:
     * <ol>
     *   <li>Resolve the format adapter, output file, and write/export options</li>
     *   <li>Connect to the database and invoke format-specific {@link #initialize}</li>
     *   <li>Stream features through the {@link Exporter} into the {@link FeatureWriter}</li>
     *   <li>Log per-feature statistics and abort on the first fatal error</li>
     * </ol>
     * Feature writes run asynchronously; {@link #abort} ensures the loop stops on error.
     *
     * @return {@code true} on successful completion, {@code false} if aborted
     */
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

        // Pre-create a unique temp directory shared by the DB texture exporter
        // (AppearanceOptions.textureOutputFolder) and the VisWriter stores.
        // Having both write into one directory keeps all intermediate files
        // together so --temp-dir redirects everything, and lets close-time
        // deletion clean the whole tree in one shot without racing concurrent
        // exports (each run gets its own .citydb-vis-tmp-* subdirectory).
        Path file = helper.resolveAgainstWorkingDir(outputFile);
        Path outputFileParent = file.toAbsolutePath().normalize().getParent();
        Path tempDir;
        try {
            Path tempRoot = helper.resolveAgainstWorkingDir(tempDirectory);
            if (tempRoot == null) {
                tempRoot = outputFileParent;
            }
            Files.createDirectories(tempRoot);
            tempDir = Files.createTempDirectory(tempRoot, ".citydb-vis-tmp-");
        } catch (IOException e) {
            throw new ExecutionException("Failed to create temp directory for vis export.", e);
        }
        writeOptions.setTempDirectory(tempDir);

        configureTextureBuckets(exportOptions, databaseManager, ioManager, ioAdapter,
                outputFileParent, tempDir);
        initialize(exportOptions, writeOptions, databaseManager);

        Query query = getQuery(exportOptions);
        FeatureStatistics statistics = new FeatureStatistics(databaseManager.getAdapter());
        AtomicLong counter = new AtomicLong();

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

        // tempDirectory is deliberately NOT set here — doExport overrides it
        // with the unique .citydb-vis-tmp-* directory it allocates for this run.

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
