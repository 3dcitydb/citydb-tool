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
import org.citydb.database.adapter.GeometryAdapter;
import org.citydb.database.geometry.GeometryException;
import org.citydb.database.schema.FeatureType;
import org.citydb.database.schema.SchemaMapping;
import org.citydb.model.common.Matrix4x4;
import org.citydb.model.common.PrefixedName;
import org.citydb.model.geometry.Coordinate;
import org.citydb.model.geometry.Point;
import org.citydb.model.property.ImplicitGeometryProperty;
import org.citydb.model.util.GeometryInfo;
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
import org.citydb.vis.config.VisFormatOptions;
import org.citydb.vis.styling.DefaultObjectStyle;
import org.citydb.vis.styling.ObjectStyleRegistry;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Map;
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
     * Apply the shared scene CLI options to the given format options.
     * Only values explicitly matched on the command line override the config.
     */
    private void applySceneOptions(VisFormatOptions options, SchemaMapping schemaMapping)
            throws ExecutionException {
        if (sceneOptions == null) {
            return;
        }

        if (Command.hasMatchedOption("--grid-edge-length", commandSpec)) {
            options.setGridEdgeLength(sceneOptions.getGridEdgeLength());
        }

        if (Command.hasMatchedOption("--lod-refine-radius", commandSpec)) {
            options.setLodRefineRadius(sceneOptions.getLodRefineRadius());
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

        // The styling registry combines --default-color (if matched) with
        // any --feature-type-style overrides. Only built when the user
        // actually configured one of them; otherwise the format options
        // keep their default empty registry.
        boolean hasDefaultColor = Command.hasMatchedOption("--default-color", commandSpec);
        Map<String, String> typeStyles = sceneOptions.getFeatureTypeStyles();
        if (!hasDefaultColor && typeStyles.isEmpty()) {
            return;
        }

        ObjectStyleRegistry.Builder builder = ObjectStyleRegistry.builder()
                .schemaMapping(schemaMapping);
        if (hasDefaultColor) {
            builder.defaultStyle(sceneOptions.getDefaultObjectStyle());
        }
        for (Map.Entry<String, String> e : typeStyles.entrySet()) {
            // Strict qualified-name match: typos like 'building' or
            // 'bldgg:Building' fail loudly rather than silently picking
            // the wrong type via local-name fallback. The styling space
            // wants precision over convenience.
            PrefixedName name = PrefixedName.of(e.getKey());
            if (name.getPrefix().isEmpty()) {
                throw new ExecutionException("Error: --feature-type-style key '" + e.getKey() +
                        "' must be a qualified name with a namespace prefix (e.g. 'bldg:Building').");
            }
            FeatureType ft = schemaMapping.getFeatureType(name);
            if (ft == FeatureType.UNDEFINED) {
                throw new ExecutionException("Error: --feature-type-style references " +
                        "unknown feature type '" + e.getKey() + "'.");
            }
            DefaultObjectStyle style = DefaultObjectStyle.parseColor(e.getValue());
            builder.override(ft.getName(), style);
        }
        options.setStyleRegistry(builder.build());
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

        DatabaseManager databaseManager = helper.connect(connectionOptions, config);
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
                        if (feature != null) {
                            try {
                                reprojectImplicitReferencePoints(feature, databaseManager.getAdapter());
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

    protected WriteOptions getWriteOptions(DatabaseAdapter adapter) throws ExecutionException {
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

    /**
     * Place each implicit-geometry instance's anchor in EPSG:4326 ahead of
     * mesh transformation:
     * <ol>
     *   <li>Compute {@code anchor_source = referencePoint + M[*][3]} — both
     *       are in the database's source SRID (typically UTM meters).
     *       The schema's hierarchy SQL skips {@code ST_Transform} on
     *       {@code val_implicitgeom_refpoint}, and the matrix is stored as
     *       a raw float array, so neither has been reprojected yet.</li>
     *   <li>Reproject {@code anchor_source} to EPSG:4326 via
     *       {@link GeometryAdapter#transform}.</li>
     *   <li>Write the reprojected anchor back as the new referencePoint and
     *       zero out the matrix's translation column. Downstream
     *       {@link org.citydb.vis.geometry.ImplicitInstanceTransformer}
     *       then applies the matrix's 3×3 rotation/scale to prototype
     *       vertices and converts the resulting metric offsets to degree
     *       offsets at the anchor's latitude.</li>
     * </ol>
     * <p>
     * <b>Why fold the translation into the anchor instead of reprojecting
     * just the referencePoint:</b> source-CRS grid axes (UTM east/north)
     * differ from geographic ENU by the meridian-convergence angle —
     * up to several degrees in datasets far from the projection's central
     * meridian. Reprojecting just the referencePoint would leave
     * {@code M_translation} on grid axes while we treat it as ENU,
     * shifting every instance by a few meters per ~100 m of translation.
     * Reprojecting {@code anchor = ref + M_translation} as one point
     * absorbs the grid rotation exactly, leaving only the prototype-scale
     * offset {@code M_3x3 · v} (typically a few meters) to be approximated
     * — at that scale the convergence error is sub-centimeter.
     * <p>
     * Each anchor is transformed via a separate SQL round-trip; for features
     * with hundreds of instances this means hundreds of round-trips.
     * Acceptable for typical city-scale data; revisit with batched
     * MultiPoint transform if profiling shows hot path.
     */
    private void reprojectImplicitReferencePoints(Feature feature, DatabaseAdapter adapter) {
        int sourceSRID = adapter.getDatabaseMetadata().getSpatialReference().getSRID();
        int targetSRID = 4326;
        if (sourceSRID == targetSRID) {
            return;
        }
        // Collect implicit geometries across the whole feature subtree.
        // Mode must match what VisWriter.write uses (INCLUDE_CONTAINED_FEATURES)
        // — otherwise we'd reproject anchors VisWriter doesn't process, or
        // (worse) leave anchors VisWriter does process unprojected.
        GeometryInfo geometryInfo = feature.getGeometryInfo(
                GeometryInfo.Mode.INCLUDE_CONTAINED_FEATURES);
        if (!geometryInfo.hasImplicitGeometries()) {
            return;
        }
        GeometryAdapter geometryAdapter = adapter.getGeometryAdapter();
        for (ImplicitGeometryProperty property : geometryInfo.getImplicitGeometries()) {
            Point ref = property.getReferencePoint().orElse(null);
            Matrix4x4 transformationMatrix = property.getTransformationMatrix().orElse(null);
            if (ref == null || transformationMatrix == null) {
                continue;
            }
            // Combine refPoint + M_translation into a single source-CRS anchor,
            // then reproject the anchor to 4326. This is what folds out the
            // UTM grid-vs-ENU rotation; see the method javadoc.
            Coordinate refCoord = ref.getCoordinate();
            double anchorX = refCoord.getX() + transformationMatrix.get(0, 3);
            double anchorY = refCoord.getY() + transformationMatrix.get(1, 3);
            double anchorZ = (refCoord.getDimension() == 3 ? refCoord.getZ() : 0.0)
                    + transformationMatrix.get(2, 3);
            // Force SRID to the database's source SRS regardless of any
            // SRID stamp the JDBC driver attached to the value. The
            // val_implicitgeom_refpoint column is declared with SRID 4326
            // in the schema, so PostGIS labels every retrieved point as
            // 4326 even though the feature-hierarchy SQL skips the transform
            // and the actual coordinate values stay in source CRS. Without
            // this override, GeometryAdapter.transform short-circuits as
            // a no-op (sourceSRID == targetSRID).
            Point anchorSource = Point.of(Coordinate.of(anchorX, anchorY, anchorZ))
                    .setSRID(sourceSRID);
            try {
                Point anchor4326 = geometryAdapter.transform(anchorSource, targetSRID);
                // Replace the anchor and zero the matrix translation. The
                // downstream transformer now applies M_3x3 to prototype
                // vertices and converts the (small) result to degrees at
                // the 4326 anchor latitude — convergence-angle error on a
                // few-meter offset is negligible.
                property.setReferencePoint(anchor4326);
                Matrix4x4 cleared = Matrix4x4.of(transformationMatrix.copy()
                        .set(0, 3, 0)
                        .set(1, 3, 0)
                        .set(2, 3, 0));
                property.setTransformationMatrix(cleared);
            } catch (GeometryException | SQLException e) {
                logger.warn("Failed to reproject implicit reference point on feature {}: {}",
                        feature.getObjectId().orElse("?"), e.getMessage());
            }
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
