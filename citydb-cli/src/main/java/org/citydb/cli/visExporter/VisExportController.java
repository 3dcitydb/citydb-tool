/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences <https://www.hft-stuttgart.de>
 */

package org.citydb.cli.visExporter;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;
import org.citydb.cli.CommandHelper;
import org.citydb.cli.ExecutionException;
import org.citydb.cli.common.*;
import org.citydb.cli.logging.LoggerManager;
import org.citydb.cli.util.FeatureStatistics;
import org.citydb.cli.visExporter.options.QueryOptions;
import org.citydb.cli.visExporter.options.SceneOptions;
import org.citydb.config.ConfigException;
import org.citydb.config.common.ConfigObject;
import org.citydb.config.common.SrsReference;
import org.citydb.core.file.OutputFile;
import org.citydb.database.DatabaseManager;
import org.citydb.database.adapter.DatabaseAdapter;
import org.citydb.database.schema.SchemaMapping;
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
import org.citydb.vis.VisExportException;
import org.citydb.vis.config.VisFormatOptions;
import org.citydb.vis.geometry.ImplicitReferencePointReprojector;
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

    @CommandLine.Spec
    protected CommandLine.Model.CommandSpec commandSpec;

    @Inject
    private CommandHelper helper;

    protected final Logger logger = LoggerManager.getInstance().getLogger(VisExportController.class);

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
    protected final OutputFormatOptions getFormatOptions(ConfigObject<OutputFormatOptions> formatOptions,
                                                         SchemaMapping schemaMapping)
            throws ExecutionException {
        T options = newFormatOptions(formatOptions);
        applySceneOptions(options, schemaMapping);
        applyAdditionalFormatOptions(options);
        return options;
    }

    /**
     * Apply the shared scene CLI options to the given format options, then
     * build the derived runtime objects (styling registry, attribute
     * projection) from the resulting string inputs.
     * <p>
     * Only values explicitly matched on the command line override the config:
     * the raw {@code --default-color} / {@code --feature-type-style} /
     * {@code --attributes} strings are copied onto the format options, leaving
     * any config-file values in place when the corresponding flag is absent.
     * {@link VisFormatOptions#buildStyleRegistry} and
     * {@link VisFormatOptions#buildAttributeProjection} then turn those strings
     * (whatever their source) into the runtime {@code ObjectStyleRegistry} and
     * {@code AttributeProjection}, so config-only styling and attribute
     * projections are honoured even when no scene CLI options are supplied.
     */
    private void applySceneOptions(VisFormatOptions options, SchemaMapping schemaMapping)
            throws ExecutionException {
        if (sceneOptions != null) {
            if (Command.hasMatchedOption("--grid-edge-length", commandSpec)) {
                options.setGridEdgeLength(sceneOptions.getGridEdgeLength());
            }

            if (Command.hasMatchedOption("--screen-pixel-threshold", commandSpec)) {
                options.setScreenPixelThreshold(sceneOptions.getScreenPixelThreshold());
            }

            if (Command.hasMatchedOption("--clamp-to-ground", commandSpec)) {
                options.setClampToGround(sceneOptions.isClampToGround());
            }

            if (Command.hasMatchedOption("--texture-scale", commandSpec)) {
                options.setTextureScale(sceneOptions.getTextureScale());
            }

            if (Command.hasMatchedOption("--max-atlas-size", commandSpec)) {
                options.setMaxAtlasSize(sceneOptions.getMaxAtlasSize());
            }

            if (Command.hasMatchedOption("--atlas-overflow-mode", commandSpec)) {
                options.setAtlasOverflowMode(sceneOptions.getAtlasOverflowMode());
            }

            if (Command.hasMatchedOption("--atlas-fallback", commandSpec)) {
                options.setAtlasFallbackStrategy(sceneOptions.getAtlasFallbackStrategy());
            }

            if (Command.hasMatchedOption("--enable-shading", commandSpec)) {
                options.setEnableShading(sceneOptions.isEnableShading());
            }

            // Raw string inputs for the derived styling/attribute objects:
            // a matched flag overrides the config, otherwise the config value
            // stands. The strings are validated/parsed by the build* calls below.
            if (Command.hasMatchedOption("--default-color", commandSpec)) {
                options.setDefaultColor(sceneOptions.getDefaultColor());
            }

            if (Command.hasMatchedOption("--feature-type-style", commandSpec)) {
                options.setFeatureTypeStyles(sceneOptions.getFeatureTypeStyles());
            }

            if (Command.hasMatchedOption("--attributes", commandSpec)) {
                options.setAttributes(sceneOptions.getAttributes());
            }
        }

        // Build the derived runtime objects from the final string inputs
        // (whatever their source). The library exceptions name the offending
        // concept but not the CLI flag; we add the flag attribution here when
        // the value came from the command line, so a CLI typo still reports
        // against the flag the user typed (e.g. "Error: --attributes ...").
        try {
            options.buildStyleRegistry(schemaMapping);
        } catch (VisExportException e) {
            throw new ExecutionException("Error: " + e.getMessage(), e);
        }

        try {
            options.buildAttributeProjection();
        } catch (VisExportException e) {
            String prefix = Command.hasMatchedOption("--attributes", commandSpec) ?
                    "--attributes " :
                    "Invalid attribute projection: ";
            throw new ExecutionException("Error: " + prefix + e.getMessage(), e);
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
     *   <li>Connect to the database and allocate the per-run temp directory</li>
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

        DatabaseManager databaseManager = helper.connect(connectionOptions);
        VisExportOptions exportOptions = getExportOptions();
        WriteOptions writeOptions = getWriteOptions(databaseManager.getAdapter());
        SchemaMapping schemaMapping = databaseManager.getAdapter().getSchemaAdapter().getSchemaMapping();
        writeOptions.getFormatOptions().set(getFormatOptions(writeOptions.getFormatOptions(), schemaMapping));

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
                        if (feature == null) {
                            abort(null, id, t);
                            return;
                        }

                        try {
                            ImplicitReferencePointReprojector.reproject(feature, databaseManager.getAdapter());
                            writer.write(feature, (success, e) -> {
                                if (success != Boolean.TRUE) {
                                    abort(feature, id, e);
                                    return;
                                }

                                statistics.add(feature);
                                long count = counter.incrementAndGet();
                                if (count % 1000 == 0) {
                                    logger.info("{} features exported.", count);
                                }
                            });
                        } catch (Throwable e) {
                            abort(feature, id, e);
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
            exportOptions = helper.getConfig().getOrElse(VisExportOptions.class, VisExportOptions::new);
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

    protected WriteOptions getWriteOptions(DatabaseAdapter adapter) throws ExecutionException {
        WriteOptions writeOptions;
        try {
            writeOptions = helper.getConfig().getOrElse(WriteOptions.class, WriteOptions::new);
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
