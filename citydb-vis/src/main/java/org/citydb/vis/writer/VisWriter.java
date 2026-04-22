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
 *   <li><b>Close phase 1–4</b>: extent computation → grid partitioning →
 *       per-cell quadtree construction → global tree merge. Driven by the
 *       {@link ExportPipeline}.</li>
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
 *       after quadtree construction.</li>
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
                    new MixedNodeSplitStage()
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
     */
    protected record PreparedNode(List<NodeEntry> entries, TriangleMesh mesh,
                                  TextureAtlas atlas) {
    }

    /**
     * Prepare a node's mesh data: load and merge meshes from the sharded store,
     * build a texture atlas (if textured), and remap UV coordinates.
     */
    protected PreparedNode prepareNodeMesh(SceneNode node) throws VisExportException {
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

            TextureAtlas atlas = null;
            if (!uniqueTexIds.isEmpty()) {
                Map<Integer, float[]> uvExtents = merged.computeUVExtents();
                atlas = TextureAtlas.build(
                        uniqueTexIds, stores.getTextureStore(), formatOptions.getTextureScale(),
                        formatOptions.getMaxAtlasSize(), uvExtents, hasUntexturedTriangle);
                if (atlas != null) {
                    atlas.remapUVs(merged);
                } else {
                    logger.warn("Node {}: all referenced textures failed to load, " +
                            "falling back to untextured rendering.", node.getIndex());
                    merged.setHasTexCoords(false);
                }
            }
            node.setTextured(atlas != null);

            return new PreparedNode(entries, merged, atlas);
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
