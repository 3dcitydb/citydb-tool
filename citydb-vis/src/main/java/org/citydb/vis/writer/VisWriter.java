/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences (HFT Stuttgart) <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.writer;

import org.citydb.config.ConfigException;
import org.citydb.core.concurrent.CountLatch;
import org.citydb.core.concurrent.ExecutorHelper;
import org.citydb.core.file.FileType;
import org.citydb.core.file.OutputFile;
import org.citydb.io.writer.FeatureWriter;
import org.citydb.io.writer.WriteException;
import org.citydb.io.writer.WriteOptions;
import org.citydb.model.feature.Feature;
import org.citydb.model.geometry.Envelope;
import org.citydb.model.geometry.LinearRing;
import org.citydb.model.property.GeometryProperty;
import org.citydb.model.property.ImplicitGeometryProperty;
import org.citydb.model.util.GeometryInfo;
import org.citydb.vis.VisExportException;
import org.citydb.vis.appearance.AppearanceExtractor;
import org.citydb.vis.appearance.RingAppearance;
import org.citydb.vis.config.ClampMode;
import org.citydb.vis.config.VisFormatOptions;
import org.citydb.vis.attribute.AttributeEncoder;
import org.citydb.vis.terrain.CesiumWorldTerrainProvider;
import org.citydb.vis.terrain.TerrainElevationProvider;
import org.citydb.vis.geometry.RingAttributes;
import org.citydb.vis.pipeline.ExportPipeline;
import org.citydb.vis.pipeline.PipelineContext;
import org.citydb.vis.pipeline.stages.AggregationStage;
import org.citydb.vis.pipeline.stages.AtlasOverflowSplitStage;
import org.citydb.vis.pipeline.stages.ExtentComputationStage;
import org.citydb.vis.pipeline.stages.MixedNodeSplitStage;
import org.citydb.vis.pipeline.stages.PartitioningStage;
import org.citydb.vis.pipeline.stages.TreeBuildingStage;
import org.citydb.vis.store.AttributeStore;
import org.citydb.vis.store.NodeEntryStore;
import org.citydb.vis.store.ShardedMeshStore;
import org.citydb.vis.store.VisExportStores;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * Abstract base for visualization format writers (I3S, 3D Tiles, etc.).
 * <p>
 * Implements the format-agnostic processing pipeline:
 * <ol>
 *   <li><b>Write phase</b> (parallel, memory-efficient):
 *       Feature extraction → triangulation → disk-backed stores
 *       ({@link ShardedMeshStore}, {@link AttributeStore},
 *       {@link org.citydb.vis.store.SpatialEntryStore}).</li>
 *   <li><b>Close phase 1–5</b>: extent computation → grid partitioning →
 *       per-cell leaf build → optional mixed-texture push-down split →
 *       spatial aggregation wrap. Driven by the {@link ExportPipeline}.</li>
 *   <li><b>Close phase 5</b> (format-specific): delegated to
 *       {@link #writeOutput} for geometry encoding, metadata serialization,
 *       and texture output in the target format.</li>
 * </ol>
 * <p>
 * <b>Memory profile (100M features, default settings):</b>
 * <ul>
 *   <li>Heap: bounded per-feature during write phase (triangulator + mesh
 *       scoped to each async task); ~500 MB peak during close phase
 *       (SceneNode tree + index arrays). All entry data is streamed from
 *       disk per-cell and flushed to {@link NodeEntryStore} immediately
 *       after the cell leaf is built.</li>
 *   <li>Disk: spatial entry shards + partitioned file + node entry file +
 *       mesh shards + attribute store (total ~20 GB at 100M features).</li>
 * </ul>
 */
public abstract class VisWriter implements FeatureWriter {
    private final Logger logger = LoggerFactory.getLogger(VisWriter.class);
    private final OutputFile outputFile;
    private final VisFormatOptions formatOptions;
    private final AttributeEncoder attributeEncoder;
    private final FeatureProcessor featureProcessor;
    private final AtomicLong featureIdCounter;
    private final int cpuCores;
    private final ExecutorService service;
    private final CountLatch countLatch;
    private final VisExportStores stores;
    // Non-null only for --clamp-to-ground=cesium-world-terrain; closed in close().
    private final TerrainElevationProvider terrainProvider;
    // Close-phase per-node assembler, shared with format-specific subclasses
    // via nodeAssembler(). Built lazily on first use so atlasNeedsWhitePixelSentinel()
    // (a subclass override) is read only after the subclass is fully constructed.
    private NodeAssembler nodeAssembler;
    // GPU-instancing prototype registry. Cheap to construct, so it is created
    // eagerly for every writer; it stays empty (and buildAtlases is a no-op)
    // for writers without an instancing path (I3S).
    private final PrototypeRegistry prototypeRegistry;

    private volatile boolean shouldRun = true;

    protected static OutputFile validateOutputFile(OutputFile file,
                                                     String formatName) throws WriteException {
        Objects.requireNonNull(file, "The output file must not be null.");
        if (file.getFileType() == FileType.ARCHIVE) {
            throw new WriteException(formatName + " export does not support archive output " +
                    "(e.g., .zip, .gz). Specify a regular file path.");
        }
        return file;
    }

    private static Path createFallbackTempDir(OutputFile outputFile) throws IOException {
        Path outputParent = outputFile.getFile().toAbsolutePath().normalize().getParent();
        Files.createDirectories(outputParent);
        return Files.createTempDirectory(outputParent, ".citydb-vis-tmp-");
    }

    protected static <T extends VisFormatOptions> T loadFormatOptions(
            WriteOptions options, Class<T> type, Supplier<T> defaultFactory,
            String formatName) throws WriteException {
        Objects.requireNonNull(options, "The write options must not be null.");
        try {
            return options.getFormatOptions().getOrElse(type, defaultFactory);
        } catch (ConfigException e) {
            throw new WriteException("Failed to get " + formatName +
                    " format options from config.", e);
        }
    }

    protected VisWriter(OutputFile outputFile,
                        VisFormatOptions formatOptions,
                        AttributeEncoder attributeEncoder,
                        WriteOptions writeOptions) throws WriteException {
        Objects.requireNonNull(outputFile, "The output file must not be null.");
        Objects.requireNonNull(formatOptions, "The format options must not be null.");
        Objects.requireNonNull(attributeEncoder, "The attribute encoder must not be null.");
        Objects.requireNonNull(writeOptions, "The write options must not be null.");

        this.outputFile = outputFile;
        this.formatOptions = formatOptions;
        this.attributeEncoder = attributeEncoder;
        // Forward the user-specified attribute projection (--attributes) to
        // the shared encoder so both I3S and 3D Tiles paths see the same
        // column whitelist. Null means "no projection installed" → default
        // extract-everything behaviour.
        attributeEncoder.setProjection(formatOptions.getAttributeProjection());
        this.featureIdCounter = new AtomicLong(0);
        // Matches the project-wide default used by Exporter and CityGML/CityJSON
        // readers: fall back to all available cores with a floor of 2 so
        // containers pinned to a single vCPU still get some parallelism.
        // Ceiling: the thread count becomes the mesh-store shard count
        // (handle encoding caps at ShardedMeshStore.MAX_SHARDS) AND the
        // parallelism of several ForkJoinPools downstream, whose hard cap is
        // 32767 — one below MAX_SHARDS. Clamp to the lower of the two so an
        // absurd --threads value degrades gracefully instead of throwing
        // IllegalArgumentException from store or pool construction.
        int requestedThreads = writeOptions.getNumberOfThreads();
        this.cpuCores = Math.min(ShardedMeshStore.MAX_SHARDS - 1,
                requestedThreads > 0
                        ? requestedThreads
                        : Math.max(2, Runtime.getRuntime().availableProcessors()));
        this.service = ExecutorHelper.newFixedAndBlockingThreadPool(cpuCores, 100);
        this.countLatch = new CountLatch();

        // The CLI controller pre-creates a unique .citydb-vis-tmp-* directory
        // and sets it as tempDirectory so the DB texture exporter and the
        // VisWriter stores share one location (redirect via --temp-dir, atomic
        // wipe on close). Non-CLI callers that don't provide a temp directory
        // get a unique one rooted at the output file's parent as a fallback.
        try {
            Path tempDir = writeOptions.getTempDirectory().isPresent()
                    ? writeOptions.getTempDirectory().get()
                    : createFallbackTempDir(outputFile);
            this.stores = new VisExportStores(cpuCores, tempDir);
        } catch (IOException e) {
            // The thread pool is already up and close() will never run
            // (construction fails here) — release it before rethrowing,
            // mirroring the terrain-provider failure path below.
            service.shutdownNow();
            throw new WriteException("Failed to create disk-backed stores.", e);
        }

        // Stand up the terrain sampler up front (one ion handshake) so a bad
        // token / unreachable service fails fast before any feature is processed.
        if (formatOptions.getClampMode() == ClampMode.CESIUM_WORLD_TERRAIN) {
            try {
                this.terrainProvider = new CesiumWorldTerrainProvider(formatOptions.getCesiumIonToken());
            } catch (VisExportException e) {
                // The stores (temp dir + open file handles) and the thread pool
                // are already up; close() will never run because construction
                // fails here, so release them before rethrowing.
                stores.close();
                service.shutdownNow();
                throw new WriteException("Failed to initialize Cesium World Terrain clamping.", e);
            }
        } else {
            this.terrainProvider = null;
        }
        this.featureProcessor = new FeatureProcessor(stores, formatOptions, attributeEncoder, terrainProvider);
        this.prototypeRegistry = new PrototypeRegistry(stores.getTextureStore());
    }

    // ---- Protected accessors for subclasses ---------------------------------

    protected OutputFile getOutputFile() {
        return outputFile;
    }

    protected VisFormatOptions getFormatOptions() {
        return formatOptions;
    }

    /**
     * Resolved worker thread count, reflecting the user's {@code --threads}
     * option (or the all-cores default, floored at 2). Subclasses use this to
     * size their own fan-out so it stays within the same thread budget.
     */
    protected int numberOfThreads() {
        return cpuCores;
    }

    // ---- Format-specific hook -----------------------------------------------

    /**
     * Write the output in the target format. Called after spatial indexing is
     * complete — the full node tree, mesh/attribute stores, and texture
     * registry are ready for consumption. The {@link PipelineContext} carries
     * all intermediate state produced by the pipeline stages; each writer
     * picks what it needs.
     */
    protected abstract void writeOutput(PipelineContext ctx) throws VisExportException;

    // ---- FeatureWriter implementation ---------------------------------------

    @Override
    public CompletableFuture<Boolean> write(Feature feature) throws WriteException {
        if (!shouldRun) {
            return CompletableFuture.completedFuture(false);
        }

        // Extract feature metadata on the caller thread (Feature may not be thread-safe)
        long featureId = featureIdCounter.incrementAndGet();
        String objectId = feature.getObjectId().orElseGet(() -> "feature_" + featureId);
        String featureType = feature.getFeatureType().getLocalName();
        String featureTypeNamespace = feature.getFeatureType().getNamespace();
        Envelope envelope = feature.getEnvelope().orElse(null);
        Map<String, Object> attributes = attributeEncoder.extractAttributes(feature);

        // Always include geometries from contained features (boundedBy
        // RoofSurface / WallSurface / ...) alongside the top-level feature's
        // own geometry. This captures the per-surface ownership needed for
        // per-feature-type styling: a CityGML 3.0 Building typically carries
        // both a lod2Solid (whose surfaceMembers xlink to BoundarySurface
        // polygons) AND each BoundarySurface's own lod2MultiSurface — using
        // SKIP_NESTED_FEATURES would pick only the lod2Solid and lose the
        // surface-type info, since its polygons are owned by Building.
        // PolygonTriangulator's gml:id-based dedup drops the duplicates
        // introduced by the dual paths, and GeometryMeshBuilder's
        // most-specific-owner-first ordering ensures the BoundarySurface
        // owner wins on each polygon's first triangulation.
        GeometryInfo geometryInfo = feature.getGeometryInfo(GeometryInfo.Mode.INCLUDE_CONTAINED_FEATURES);
        List<GeometryProperty> geometryProperties = geometryInfo.getGeometries();
        List<ImplicitGeometryProperty> implicitProperties = geometryInfo.getImplicitGeometries();

        if (geometryProperties.isEmpty() && implicitProperties.isEmpty()) {
            return CompletableFuture.completedFuture(true);
        }

        // Sub-tasks that together produce this one feature: one for its
        // combined regular geometry, plus one per implicit geometry (each
        // carries its own transform, so they can't be batched together).
        List<CompletableFuture<Boolean>> subTasks = new ArrayList<>();

        if (!geometryProperties.isEmpty()) {
            RingAppearance appearance = AppearanceExtractor.extract(feature, stores.getTextureStore());

            List<GeometryProperty> geomProps = new ArrayList<>(geometryProperties);
            Map<LinearRing, Integer> ringTextureIds = appearance.ringTextureIds();
            if (ringTextureIds != null && !ringTextureIds.isEmpty()) {
                stores.setFeatureTextured(featureId);
            }

            subTasks.add(dispatchProcessing(featureId, objectId, featureType,
                    featureTypeNamespace, envelope, attributes, geomProps,
                    appearance.forTriangulation()));
        }

        if (!implicitProperties.isEmpty()) {
            // Per-feature planner: decides instanced vs. baked per instance
            // and prepares the processing inputs; the async dispatch stays
            // here. Planning runs sequentially on the caller thread.
            ImplicitInstancePlanner planner = new ImplicitInstancePlanner(
                    stores, attributeEncoder, formatOptions.getStyleRegistry(),
                    prototypeRegistry, featureIdCounter,
                    supportsImplicitGeometryInstancing(),
                    objectId, featureType, featureTypeNamespace, attributes);

            for (ImplicitGeometryProperty property : implicitProperties) {
                ImplicitInstancePlanner.Plan plan;
                try {
                    plan = planner.plan(property);
                } catch (IOException e) {
                    shouldRun = false;
                    CompletableFuture<Boolean> failed = new CompletableFuture<>();
                    failed.completeExceptionally(
                            new WriteException("Failed to persist instance attributes.", e));
                    subTasks.add(failed);
                    continue;
                }
                if (plan == null) {
                    // Silently skipped (pure reference, library object, or
                    // missing transformation/reference point) — the planner
                    // logged the reason at DEBUG.
                    continue;
                }
                if (plan instanceof ImplicitInstancePlanner.InstancedPlan instanced) {
                    subTasks.add(dispatchInstance(instanced));
                } else if (plan instanceof ImplicitInstancePlanner.BakedPlan baked) {
                    // Pass envelope=null so process() recomputes the bbox from
                    // the transformed mesh — the parent feature's envelope
                    // covers all its explicit + implicit content combined and
                    // would not match the single-instance footprint that
                    // spatial partitioning needs.
                    subTasks.add(dispatchProcessing(baked.instanceId(), objectId,
                            featureType, featureTypeNamespace, null, attributes,
                            List.of(baked.wrapped()), baked.ringAttributes()));
                }
            }
        }

        if (subTasks.isEmpty()) {
            return CompletableFuture.completedFuture(true);
        }
        if (subTasks.size() == 1) {
            return subTasks.get(0);
        }
        // Join: completes successfully only if every sub-task completed
        // successfully. Each sub-task's future completes with true or
        // exceptionally (dispatchProcessing never completes it false), so allOf
        // completing without a throwable already means every sub-task succeeded.
        return CompletableFuture.allOf(subTasks.toArray(new CompletableFuture[0]))
                .handle((v, t) -> t == null);
    }

    private CompletableFuture<Boolean> dispatchProcessing(
            long featureId, String objectId, String featureType, String featureTypeNamespace,
            Envelope envelope, Map<String, Object> attributes,
            List<GeometryProperty> geomProps, RingAttributes ringAttributes) {
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        countLatch.increment();
        service.execute(() -> {
            try {
                featureProcessor.process(featureId, objectId, featureType,
                        featureTypeNamespace,
                        envelope, attributes, geomProps, ringAttributes);
                result.complete(true);
            } catch (Throwable e) {
                shouldRun = false;
                result.completeExceptionally(new WriteException("Failed to process feature.", e));
            } finally {
                countLatch.decrement();
            }
        });
        return result;
    }

    /**
     * Async dispatch of a GPU-instancing plan via
     * {@link FeatureProcessor#processInstance}, mirroring
     * {@link #dispatchProcessing}'s latch/error handling.
     */
    private CompletableFuture<Boolean> dispatchInstance(
            ImplicitInstancePlanner.InstancedPlan plan) {
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        countLatch.increment();
        service.execute(() -> {
            try {
                featureProcessor.processInstance(plan.instanceId(), plan.objectId(),
                        plan.attrOffset(), plan.prototype(), plan.transformationMatrix(),
                        plan.trs(), plan.referencePoint(), plan.styleColor());
                result.complete(true);
            } catch (Throwable e) {
                shouldRun = false;
                result.completeExceptionally(new WriteException("Failed to process instance.", e));
            } finally {
                countLatch.decrement();
            }
        });
        return result;
    }

    /**
     * Whether this writer emits GPU-instanced implicit geometry (glTF
     * {@code EXT_mesh_gpu_instancing}). Default {@code false}; overridden by
     * {@code Tiles3DWriter} based on the {@code --implicit-geometry-instancing}
     * option. I3S has no instancing concept, so its writer keeps the baked
     * path.
     */
    protected boolean supportsImplicitGeometryInstancing() {
        return false;
    }

    @Override
    public void cancel() {
        shouldRun = false;
    }

    @Override
    public void close() throws WriteException {
        try {
            countLatch.await();
        } finally {
            service.shutdown();
        }

        try {
            long totalFeatures = stores.entryCount();
            if (!shouldRun || totalFeatures == 0) {
                return;
            }

            PipelineContext ctx = new PipelineContext(
                    stores, formatOptions, attributeEncoder, totalFeatures, cpuCores);

            new ExportPipeline(
                    new ExtentComputationStage(),
                    new PartitioningStage(),
                    new TreeBuildingStage(),
                    new MixedNodeSplitStage(),
                    new AtlasOverflowSplitStage(),
                    new AggregationStage()
            ).run(ctx);

            // Build the per-prototype texture atlases now: texture BLOBs are
            // on disk (write phase complete) and the parallel node fan-out
            // hasn't started (the build mutates shared prototype meshes).
            prototypeRegistry.buildAtlases(formatOptions);

            // --- Phase 5: Format-specific output ---
            writeOutput(ctx);
        } catch (VisExportException e) {
            throw new WriteException("Failed to write scene layer.", e);
        } finally {
            if (terrainProvider != null) {
                terrainProvider.close();
            }
            logger.info("Closing intermediate stores and deleting temp directory.");
            stores.close();
        }
    }

    // ---- Per-node assembly (shared by format-specific writers) --------------

    /**
     * Close-phase per-node assembler ({@link NodeAssembler}), built lazily so a
     * subclass's {@link #atlasNeedsWhitePixelSentinel()} override is observed
     * only after the subclass is fully constructed. Only used during
     * {@link #writeOutput}, on the single close thread before it fans out, so
     * the unsynchronized lazy init is safe.
     */
    protected final NodeAssembler nodeAssembler() {
        if (nodeAssembler == null) {
            nodeAssembler = new NodeAssembler(
                    stores, formatOptions, cpuCores, atlasNeedsWhitePixelSentinel(),
                    prototypeRegistry);
        }
        return nodeAssembler;
    }

    /**
     * Whether this writer's geometry encoder samples the atlas for untextured
     * triangles (and therefore needs the {@code build()} path to reserve a
     * 4×4 white-pixel sentinel for intra-feature mixed cells).
     * <p>
     * I3S: {@code true} (default) — each node has exactly one material, so
     * untextured triangles must sample a guaranteed-white atlas region.
     * <p>
     * 3D Tiles: {@code false} (overridden by {@code Tiles3DWriter}) — its
     * GLB encoder partitions untextured triangles to a separate primitive
     * with its own untextured material, so the atlas is never sampled for
     * those triangles.
     */
    protected boolean atlasNeedsWhitePixelSentinel() {
        return true;
    }

}
