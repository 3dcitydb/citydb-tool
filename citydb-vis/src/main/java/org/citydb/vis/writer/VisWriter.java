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
import org.citydb.model.appearance.TextureCoordinate;
import org.citydb.model.feature.Feature;
import org.citydb.model.geometry.Envelope;
import org.citydb.model.geometry.LinearRing;
import org.citydb.model.property.GeometryProperty;
import org.citydb.model.util.GeometryInfo;
import org.citydb.vis.VisExportException;
import org.citydb.vis.encoder.AttributeEncoder;
import org.citydb.vis.encoder.TextureAtlas;
import org.citydb.vis.geometry.TriangleMesh;
import org.citydb.vis.model.FeatureData;
import org.citydb.vis.scene.SceneNode;
import org.citydb.vis.pipeline.ExportPipeline;
import org.citydb.vis.pipeline.PipelineContext;
import org.citydb.vis.pipeline.stages.AggregationStage;
import org.citydb.vis.pipeline.stages.AtlasOverflowQuadtreeStage;
import org.citydb.vis.pipeline.stages.ExtentComputationStage;
import org.citydb.vis.pipeline.stages.MixedNodeSplitStage;
import org.citydb.vis.pipeline.stages.PartitioningStage;
import org.citydb.vis.pipeline.stages.TreeBuildingStage;
import org.citydb.vis.store.AttributeStore;
import org.citydb.vis.store.NodeEntry;
import org.citydb.vis.store.NodeEntryStore;
import org.citydb.vis.store.ShardedMeshStore;
import org.citydb.vis.store.VisExportStores;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
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
        this.featureIdCounter = new AtomicLong(0);
        // Matches the project-wide default used by Exporter and CityGML/CityJSON
        // readers: fall back to all available cores with a floor of 2 so
        // containers pinned to a single vCPU still get some parallelism.
        int requestedThreads = writeOptions.getNumberOfThreads();
        this.cpuCores = requestedThreads > 0
                ? requestedThreads
                : Math.max(2, Runtime.getRuntime().availableProcessors());
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
            this.stores = new VisExportStores(outputFile, cpuCores, tempDir);
        } catch (IOException e) {
            throw new WriteException("Failed to create disk-backed stores.", e);
        }
        this.featureProcessor = new FeatureProcessor(stores, formatOptions, attributeEncoder);
    }

    // ---- Protected accessors for subclasses ---------------------------------

    protected OutputFile getOutputFile() {
        return outputFile;
    }

    protected VisFormatOptions getFormatOptions() {
        return formatOptions;
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
        Envelope envelope = feature.getEnvelope().orElse(null);
        Map<String, Object> attributes = attributeEncoder.extractAttributes(feature);

        GeometryInfo geometryInfo = feature.getGeometryInfo(
                GeometryInfo.Mode.SKIP_NESTED_FEATURES);
        List<GeometryProperty> geometryProperties = geometryInfo.getGeometries();
        if (geometryProperties.isEmpty()) {
            geometryInfo = feature.getGeometryInfo(
                    GeometryInfo.Mode.INCLUDE_CONTAINED_FEATURES);
            geometryProperties = geometryInfo.getGeometries();
        }

        if (geometryProperties.isEmpty()) {
            return CompletableFuture.completedFuture(true);
        }

        AppearanceExtractor.Result appearance =
                AppearanceExtractor.extract(feature, stores.getTextureStore());

        List<GeometryProperty> geomProps = new ArrayList<>(geometryProperties);
        Map<LinearRing, List<TextureCoordinate>> texCoords = appearance.texCoords();
        Map<LinearRing, Integer> ringTextureIds = appearance.ringTextureIds();
        if (ringTextureIds != null && !ringTextureIds.isEmpty()) {
            stores.setFeatureTextured(featureId);
        }

        CompletableFuture<Boolean> result = new CompletableFuture<>();
        countLatch.increment();
        service.execute(() -> {
            try {
                featureProcessor.process(featureId, objectId, featureType,
                        envelope, attributes, geomProps, texCoords, ringTextureIds);
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
                    stores, formatOptions, attributeEncoder, totalFeatures);

            new ExportPipeline(
                    new ExtentComputationStage(),
                    new PartitioningStage(),
                    new TreeBuildingStage(),
                    new MixedNodeSplitStage(),
                    new AtlasOverflowQuadtreeStage(),
                    new AggregationStage()
            ).run(ctx);

            // --- Phase 5: Format-specific output ---
            writeOutput(ctx);
        } catch (VisExportException e) {
            throw new WriteException("Failed to write scene layer.", e);
        } finally {
            logger.info("Closing intermediate stores and deleting temp directory.");
            stores.close();
        }
    }

    // ---- Per-node preparation (shared by format-specific writers) -----------

    /**
     * Prepared per-node data: merged mesh with atlas-remapped UVs.
     * <p>
     * {@code atlases} holds one entry in the single-atlas case (I3S — required
     * by the spec's one-material-per-node rule) and one-or-more in the
     * multi-atlas case (3D Tiles, where GLB supports multiple materials per
     * mesh). Empty when the node carries no textured triangles or every
     * referenced texture failed to load.
     */
    protected record PreparedNode(List<NodeEntry> entries, TriangleMesh mesh,
                                  List<TextureAtlas> atlases) {
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

    /**
     * Strategy for atlas page generation per node.
     */
    protected enum AtlasMode {
        /**
         * Force a single atlas page. Required for I3S because the spec
         * permits only one material per node; overflow is handled by the
         * single-atlas path's rescale + atlas-size expansion fallback.
         */
        SINGLE_ATLAS,
        /**
         * Single page when the textures fit; spill onto additional pages
         * only when the BSP packer would otherwise overflow even after
         * per-texture clamping. Preserves source resolution on the
         * residual cells that the {@link
         * org.citydb.vis.pipeline.stages.AtlasOverflowQuadtreeStage}
         * could not subdivide further (single-feature or depth-cap
         * fallback). Used by 3D Tiles, whose GLB supports multiple
         * primitives per mesh.
         */
        AUTO
    }

    /**
     * Prepare a node's mesh data: load and merge meshes from the sharded
     * store, build texture atlas(es), and remap UV coordinates.
     */
    protected PreparedNode prepareNodeMesh(SceneNode node, AtlasMode mode) throws VisExportException {
        try {
            List<NodeEntry> entries = stores.getNodeEntryStore().loadNode(node.getIndex());

            TriangleMesh merged = new TriangleMesh();
            for (NodeEntry entry : entries) {
                merged.merge(stores.getMeshStore().load(entry.meshHandle()));
            }

            Set<Integer> uniqueTexIds = new LinkedHashSet<>();
            boolean hasUntexturedTriangle = false;
            for (int texId : merged.getTriangleTextureIds()) {
                if (texId >= 0) uniqueTexIds.add(texId);
                else hasUntexturedTriangle = true;
            }
            if (uniqueTexIds.isEmpty() && merged.hasTexCoords()) {
                merged.setHasTexCoords(false);
            }

            List<TextureAtlas> atlases = List.of();
            if (!uniqueTexIds.isEmpty()) {
                Map<Integer, float[]> uvExtents = merged.computeUVExtents();
                // AUTO: prefer single page; spill to multi-page only when
                // the BSP packer would otherwise overflow. The wouldOverflow
                // predicate runs on metadata only (~3-4% of full build cost),
                // so the extra check is negligible and keeps the common case
                // on the cheaper single-atlas path.
                boolean useMulti = mode == AtlasMode.AUTO
                        && TextureAtlas.wouldOverflow(uniqueTexIds, stores.getTextureStore(),
                                formatOptions.getTextureScale(),
                                formatOptions.getMaxAtlasSize(), uvExtents);
                if (useMulti) {
                    atlases = TextureAtlas.buildMulti(
                            uniqueTexIds, stores.getTextureStore(), formatOptions.getTextureScale(),
                            formatOptions.getMaxAtlasSize(), uvExtents);
                } else {
                    // Only reserve the 4x4 white-pixel sentinel when the
                    // writer's encoder actually samples the atlas for
                    // untextured triangles (I3S, single material per node).
                    // 3D Tiles partitions untextured triangles to a separate
                    // primitive in the GLB and never samples the atlas for
                    // them, so reserving the sentinel just wastes BSP space
                    // and can push a borderline-fitting atlas over the edge,
                    // triggering needless Phase 2 expansion.
                    boolean needsWhitePixel = hasUntexturedTriangle && atlasNeedsWhitePixelSentinel();
                    TextureAtlas single = TextureAtlas.build(
                            uniqueTexIds, stores.getTextureStore(), formatOptions.getTextureScale(),
                            formatOptions.getMaxAtlasSize(), uvExtents, needsWhitePixel,
                            formatOptions.getAtlasFallbackStrategy());
                    atlases = single != null ? List.of(single) : List.of();
                }
                if (!atlases.isEmpty()) {
                    for (TextureAtlas atlas : atlases) {
                        atlas.remapUVs(merged);
                    }
                } else {
                    logger.warn("Node {}: all referenced textures failed to load, " +
                            "falling back to untextured rendering.", node.getIndex());
                    merged.setHasTexCoords(false);
                }
            }
            node.setTextured(!atlases.isEmpty());

            return new PreparedNode(entries, merged, atlases);
        } catch (IOException e) {
            throw new VisExportException("Failed to prepare node " + node.getIndex() + ".", e);
        }
    }

    /**
     * Load per-feature attribute data from the disk-backed attribute store.
     */
    protected List<FeatureData> loadNodeFeatures(List<NodeEntry> entries) throws VisExportException {
        try {
            List<FeatureData> features = new ArrayList<>(entries.size());
            for (NodeEntry entry : entries) {
                AttributeStore.FeatureAttrs attrs = stores.getAttrStore().load(entry.attrOffset());
                features.add(new FeatureData(
                        entry.id(), attrs.objectId(), attrs.featureType(),
                        attrs.attributes()));
            }
            return features;
        } catch (IOException e) {
            throw new VisExportException("Failed to load node attribute data.", e);
        }
    }

    /**
     * Emit a DEBUG per detected user-setting violation on the prepared node:
     * <ul>
     *   <li>Atlas page size exceeded {@code --max-atlas-size} — fires when the
     *       single-atlas path's atlas-size expansion grew the page beyond the
     *       user-requested cap (typical I3S {@code --atlas-fallback=expand}
     *       residual case). 3D Tiles {@code AUTO} mode does not trigger this
     *       since it spills to multi-page atlases instead.</li>
     *   <li>Texture scale dropped below {@code --texture-scale} — fires when
     *       the rescale loop shrank textures uniformly (typical
     *       {@code --atlas-fallback=rescale} residual case, or the EXPAND
     *       last-resort post-expansion rescale at 16K).</li>
     * </ul>
     * DEBUG (not WARN): residual cells in the default {@code expand} mode are
     * common — hundreds per export — and the violations are by-design behavior
     * of the chosen fallback strategy, not errors. Users debugging a specific
     * building's missing/blurry texture can enable DEBUG to see the per-cell
     * detail naming the affected {@code gml:id}.
     */
    protected void logAtlasViolations(SceneNode node, PreparedNode prepared,
                                      List<FeatureData> features) {
        if (prepared.atlases().isEmpty() || !logger.isDebugEnabled()) {
            return;
        }

        int requestedMaxSize = formatOptions.getMaxAtlasSize();
        double requestedScale = formatOptions.getTextureScale();

        int actualMaxSize = 0;
        double actualScale = 1.0;
        for (TextureAtlas atlas : prepared.atlases()) {
            actualMaxSize = Math.max(actualMaxSize, Math.max(atlas.getWidth(), atlas.getHeight()));
            actualScale = Math.min(actualScale, atlas.getActualScale());
        }

        boolean atlasExpanded = actualMaxSize > requestedMaxSize;
        boolean texturesRescaled = actualScale < requestedScale;
        if (!atlasExpanded && !texturesRescaled) {
            return;
        }

        String featureSummary = formatFeatureSummary(features);
        if (atlasExpanded) {
            logger.debug("Atlas page expanded to {}×{} beyond --max-atlas-size {}×{} " +
                            "for {} (node {}).",
                    actualMaxSize, actualMaxSize, requestedMaxSize, requestedMaxSize,
                    featureSummary, node.getIndex());
        }
        if (texturesRescaled) {
            logger.debug("Textures rescaled from --texture-scale {} to {} for {} " +
                            "(node {}).",
                    requestedScale, actualScale, featureSummary, node.getIndex());
        }
    }

    /**
     * Render a human-readable feature list for a violation DEBUG message.
     * Single feature shows the bare {@code gml:id}; up to five list all of
     * them; larger nodes show count + first id as a sample.
     */
    private static String formatFeatureSummary(List<FeatureData> features) {
        if (features.isEmpty()) {
            return "node with no features";
        }
        if (features.size() == 1) {
            return "feature " + features.get(0).objectId();
        }
        if (features.size() <= 5) {
            StringBuilder sb = new StringBuilder("features ");
            for (int i = 0; i < features.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(features.get(i).objectId());
            }
            return sb.toString();
        }
        return features.size() + " features (first: " + features.get(0).objectId() + ")";
    }

    // ---- Parallel node processing -------------------------------------------

    @FunctionalInterface
    protected interface NodeProcessor {
        boolean process(SceneNode node) throws VisExportException;
    }

    /**
     * Process mesh nodes in parallel using a ForkJoinPool, track progress,
     * and strip nodes whose geometry was empty after welding/degenerate
     * filtering.
     *
     * @return effective mesh node indices (input set minus empty nodes)
     */
    protected Set<Integer> processNodesParallel(List<SceneNode> allNodes,
                                                Set<Integer> meshNodeIndices,
                                                NodeProcessor processor) throws VisExportException {
        List<SceneNode> meshNodes = allNodes.stream()
                .filter(n -> meshNodeIndices.contains(n.getIndex()))
                .toList();

        Set<Integer> emptyNodeIndices = ConcurrentHashMap.newKeySet();
        AtomicInteger nodesProcessed = new AtomicInteger(0);
        int total = meshNodes.size();
        ForkJoinPool pool = new ForkJoinPool(cpuCores);
        try {
            pool.submit(() -> meshNodes.parallelStream().forEach(node -> {
                try {
                    if (!processor.process(node)) {
                        emptyNodeIndices.add(node.getIndex());
                    }
                } catch (VisExportException e) {
                    throw new NodeProcessingException(e);
                }
                int done = nodesProcessed.incrementAndGet();
                if (done % 100 == 0 || done == total) {
                    logger.info("Nodes written: {}/{}.", done, total);
                }
            })).join();
        } catch (NodeProcessingException e) {
            throw e.cause;
        } finally {
            pool.shutdown();
            try {
                pool.awaitTermination(1, TimeUnit.MINUTES);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        Set<Integer> result = meshNodeIndices;
        if (!emptyNodeIndices.isEmpty()) {
            result = new HashSet<>(meshNodeIndices);
            result.removeAll(emptyNodeIndices);
            logger.info("Dropped {} empty mesh nodes (all triangles degenerate after welding).",
                    emptyNodeIndices.size());
        }
        return result;
    }

    /** Unchecked wrapper to tunnel {@link VisExportException} through parallel streams. */
    private static final class NodeProcessingException extends RuntimeException {
        private final VisExportException cause;

        NodeProcessingException(VisExportException cause) {
            super(cause);
            this.cause = cause;
        }
    }
}
