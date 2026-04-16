/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
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
import org.citydb.vis.encoder.AttributeEncoder;
import org.citydb.vis.encoder.TextureAtlas;
import org.citydb.vis.geometry.TriangleMesh;
import org.citydb.vis.model.AttrField;
import org.citydb.vis.model.FeatureData;
import org.citydb.vis.scene.SceneNode;
import org.citydb.vis.pipeline.AppearanceExtractor;
import org.citydb.vis.pipeline.ExportPipeline;
import org.citydb.vis.pipeline.FeatureProcessor;
import org.citydb.vis.pipeline.PipelineContext;
import org.citydb.vis.pipeline.stages.ExtentComputationStage;
import org.citydb.vis.pipeline.stages.PartitioningStage;
import org.citydb.vis.pipeline.stages.TreeBuildingStage;
import org.citydb.vis.store.AttributeStore;
import org.citydb.vis.store.NodeEntry;
import org.citydb.vis.store.NodeEntryStore;
import org.citydb.vis.store.ShardedMeshStore;
import org.citydb.vis.store.TextureStore;
import org.citydb.vis.store.VisExportStores;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
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
                        AttributeEncoder attributeEncoder) throws WriteException {
        Objects.requireNonNull(outputFile, "The output file must not be null.");
        Objects.requireNonNull(formatOptions, "The format options must not be null.");
        Objects.requireNonNull(attributeEncoder, "The attribute encoder must not be null.");

        this.outputFile = outputFile;
        this.formatOptions = formatOptions;
        this.attributeEncoder = attributeEncoder;
        this.featureIdCounter = new AtomicLong(0);
        this.cpuCores = Runtime.getRuntime().availableProcessors();
        this.service = ExecutorHelper.newFixedAndBlockingThreadPool(cpuCores, 100);
        this.countLatch = new CountLatch();

        try {
            this.stores = new VisExportStores(outputFile, cpuCores);
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

    protected ShardedMeshStore getMeshStore() {
        return stores.getMeshStore();
    }

    protected AttributeStore getAttrStore() {
        return stores.getAttrStore();
    }

    protected TextureStore getTextureStore() {
        return stores.getTextureStore();
    }

    protected NodeEntryStore getNodeEntryStore() {
        return stores.getNodeEntryStore();
    }

    protected int getCpuCores() {
        return cpuCores;
    }

    // ---- Format-specific hook -----------------------------------------------

    /**
     * Write the output in the target format. Called after spatial indexing is
     * complete — the full node tree, mesh/attribute stores, and texture
     * registry are ready for consumption.
     *
     * @param allNodes         flat list of all scene nodes (index 0 = global root)
     * @param meshNodeIndices  indices of nodes that carry geometry
     * @param extent           global bounding box [minX, minY, minZ, maxX, maxY, maxZ]
     * @param attrFields       finalized attribute field definitions
     * @param hasTextures      whether any feature registered a texture
     */
    protected abstract void writeOutput(List<SceneNode> allNodes,
                                        Set<Integer> meshNodeIndices,
                                        double[] extent,
                                        List<AttrField> attrFields,
                                        boolean hasTextures) throws IOException;

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

            long t0 = System.currentTimeMillis();
            new ExportPipeline(
                    new ExtentComputationStage(),
                    new PartitioningStage(),
                    new TreeBuildingStage()
            ).run(ctx);
            long t1 = System.currentTimeMillis();
            logger.info("Spatial indexing: {} ms ({} nodes, {} with mesh).",
                    t1 - t0, ctx.allNodes().size(), ctx.meshNodeIndices().size());

            // --- Phase 5: Format-specific output ---
            writeOutput(ctx.allNodes(), ctx.meshNodeIndices(), ctx.extent(),
                    ctx.attrFields(), ctx.hasTextures());
            long t2 = System.currentTimeMillis();
            logger.info("Node output: {} ms.", t2 - t1);

        } catch (Exception e) {
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
    protected PreparedNode prepareNodeMesh(SceneNode node) throws IOException {
        List<NodeEntry> entries = stores.getNodeEntryStore().loadNode(node.getIndex());

        TriangleMesh merged = new TriangleMesh();
        for (NodeEntry entry : entries) {
            merged.merge(stores.getMeshStore().load(entry.meshHandle()));
        }

        Set<Integer> uniqueTexIds = new LinkedHashSet<>();
        for (int texId : merged.getTriangleTextureIds()) {
            if (texId >= 0) uniqueTexIds.add(texId);
        }
        if (uniqueTexIds.isEmpty() && merged.hasTexCoords()) {
            merged.setHasTexCoords(false);
        }

        TextureAtlas atlas = null;
        if (!uniqueTexIds.isEmpty()) {
            Map<Integer, float[]> uvExtents = merged.computeUVExtents();
            atlas = TextureAtlas.build(
                    uniqueTexIds, stores.getTextureStore(), formatOptions.getTextureScale(),
                    formatOptions.getMaxAtlasSize(), uvExtents);
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
    }

    /**
     * Load per-feature attribute data from the disk-backed attribute store.
     */
    protected List<FeatureData> loadNodeFeatures(List<NodeEntry> entries) throws IOException {
        List<FeatureData> features = new ArrayList<>(entries.size());
        for (NodeEntry entry : entries) {
            AttributeStore.FeatureAttrs attrs = stores.getAttrStore().load(entry.attrOffset());
            features.add(new FeatureData(
                    entry.id(), attrs.objectId(), attrs.featureType(),
                    attrs.attributes()));
        }
        return features;
    }

    // ---- Parallel node processing -------------------------------------------

    @FunctionalInterface
    protected interface NodeProcessor {
        boolean process(SceneNode node) throws IOException;
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
                                                NodeProcessor processor) throws IOException {
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
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
                int done = nodesProcessed.incrementAndGet();
                if (done % 100 == 0 || done == total) {
                    logger.info("Nodes written: {}/{}.", done, total);
                }
            })).join();
        } catch (UncheckedIOException e) {
            throw e.getCause();
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
}
