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
import org.citydb.model.appearance.Appearance;
import org.citydb.model.appearance.ParameterizedTexture;
import org.citydb.model.appearance.SurfaceData;
import org.citydb.model.appearance.SurfaceDataProperty;
import org.citydb.model.appearance.TextureCoordinate;
import org.citydb.model.common.ExternalFile;
import org.citydb.model.feature.Feature;
import org.citydb.model.geometry.Coordinate;
import org.citydb.model.geometry.Envelope;
import org.citydb.model.geometry.Geometry;
import org.citydb.model.geometry.LinearRing;
import org.citydb.model.property.AppearanceProperty;
import org.citydb.model.property.GeometryProperty;
import org.citydb.model.util.GeometryInfo;
import org.citydb.vis.encoder.AttributeEncoder;
import org.citydb.vis.encoder.TextureAtlas;
import org.citydb.vis.geometry.PolygonTriangulator;
import org.citydb.vis.geometry.TriangleMesh;
import org.citydb.vis.model.AttrField;
import org.citydb.vis.model.FeatureData;
import org.citydb.vis.scene.SceneNode;
import org.citydb.vis.store.AttributeStore;
import org.citydb.vis.store.NodeEntry;
import org.citydb.vis.store.NodeEntryStore;
import org.citydb.vis.store.PartitionedEntryStore;
import org.citydb.vis.store.ShardedMeshStore;
import org.citydb.vis.store.SpatialEntry;
import org.citydb.vis.store.SpatialEntryStore;
import org.citydb.vis.store.TextureStore;
import org.citydb.vis.util.GeoTransform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
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
 *       ({@link ShardedMeshStore}, {@link AttributeStore}, {@link SpatialEntryStore}).</li>
 *   <li><b>Close phase 1–4</b>: extent computation → grid partitioning
 *       ({@link PartitionedEntryStore}) → per-cell quadtree construction
 *       ({@link NodeBuilder}) → global tree merge with {@link NodeEntryStore}.</li>
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
    private static final String TEMP_DIR_NAME = ".tmp";

    private final Logger logger = LoggerFactory.getLogger(VisWriter.class);
    private final OutputFile outputFile;
    private final VisFormatOptions formatOptions;
    private final AttributeEncoder attributeEncoder;
    private final SpatialEntryStore spatialEntryStore;
    private final AtomicLong featureIdCounter;
    private final int cpuCores;
    private final ExecutorService service;
    private final CountLatch countLatch;
    private final Path tempDir;
    private final ShardedMeshStore meshStore;
    private final AttributeStore attrStore;
    private final TextureStore textureStore;

    private NodeEntryStore nodeEntryStore;
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
            this.tempDir = outputFile.getFile().getParent().resolve(TEMP_DIR_NAME);
            Files.createDirectories(tempDir);
            this.spatialEntryStore = new SpatialEntryStore(cpuCores, tempDir);
            this.meshStore = new ShardedMeshStore(cpuCores, tempDir);
            this.attrStore = new AttributeStore(tempDir);
            this.textureStore = new TextureStore(outputFile);
        } catch (IOException e) {
            throw new WriteException("Failed to create disk-backed stores.", e);
        }
    }

    // ---- Protected accessors for subclasses ---------------------------------

    protected OutputFile getOutputFile() {
        return outputFile;
    }

    protected VisFormatOptions getFormatOptions() {
        return formatOptions;
    }

    protected ShardedMeshStore getMeshStore() {
        return meshStore;
    }

    protected AttributeStore getAttrStore() {
        return attrStore;
    }

    protected TextureStore getTextureStore() {
        return textureStore;
    }

    protected NodeEntryStore getNodeEntryStore() {
        return nodeEntryStore;
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

        // Collect all geometry properties on the caller thread
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

        // Extract texture coordinates and images from appearances (on caller thread).
        // Each ParameterizedTexture maps specific LinearRings to UV coordinates
        // and references a texture image. We track per-ring texture IDs so each
        // polygon surface gets its own texture in the atlas.
        Map<LinearRing, List<TextureCoordinate>> texCoordMap = null;
        Map<LinearRing, Integer> ringTextureMap = null;
        if (feature.hasAppearances()) {
            texCoordMap = new IdentityHashMap<>();
            ringTextureMap = new IdentityHashMap<>();
            for (AppearanceProperty ap : feature.getAppearances().getAll()) {
                Appearance appearance = ap.getObject();
                if (appearance == null) continue;
                for (SurfaceDataProperty sdp : appearance.getSurfaceData()) {
                    SurfaceData<?> sd = sdp.getObject().orElse(null);
                    if (sd instanceof ParameterizedTexture pt) {
                        if (pt.hasTextureCoordinates()) {
                            // Register this PT's texture image
                            int ptTextureId = -1;
                            ExternalFile img = pt.getTextureImage().orElse(null);
                            if (img != null) {
                                ptTextureId = textureStore.register(img.getFileLocation());
                            }
                            // Map each ring to its UV coordinates AND texture ID
                            Map<LinearRing, List<TextureCoordinate>> ptCoords =
                                    pt.getTextureCoordinates();
                            texCoordMap.putAll(ptCoords);
                            if (ptTextureId >= 0) {
                                for (LinearRing ring : ptCoords.keySet()) {
                                    ringTextureMap.put(ring, ptTextureId);
                                }
                            }
                        }
                    }
                }
            }
            if (texCoordMap.isEmpty()) {
                texCoordMap = null;
                ringTextureMap = null;
            }
        }

        // Capture the geometry list for the async task
        List<GeometryProperty> geomProps = new ArrayList<>(geometryProperties);
        final Map<LinearRing, List<TextureCoordinate>> finalTexCoordMap = texCoordMap;
        final Map<LinearRing, Integer> finalRingTextureMap = ringTextureMap;
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        countLatch.increment();
        service.execute(() -> {
            try {
                PolygonTriangulator triangulator = new PolygonTriangulator();
                TriangleMesh mesh = triangulateGeometries(geomProps, featureId, triangulator,
                        finalTexCoordMap, finalRingTextureMap);
                if (!mesh.isEmpty()) {
                    Envelope env = envelope;
                    if (formatOptions.isClampToGround()) {
                        clampMeshToGround(mesh);
                        // Recompute Z from the clamped mesh — the Feature's envelope
                        // Z may not match the mesh when multiple LODs or non-surface
                        // geometries contribute to the envelope.
                        if (env != null) {
                            double[] meshBbox = mesh.computeBoundingBox();
                            env = Envelope.of(
                                    Coordinate.of(env.getLowerCorner().getX(),
                                            env.getLowerCorner().getY(), meshBbox[2]),
                                    Coordinate.of(env.getUpperCorner().getX(),
                                            env.getUpperCorner().getY(), meshBbox[5]));
                        }
                    }

                    // Compute spatial metadata from envelope or mesh
                    double cx, cy;
                    double[] bbox;
                    if (env != null) {
                        cx = (env.getLowerCorner().getX() + env.getUpperCorner().getX()) / 2;
                        cy = (env.getLowerCorner().getY() + env.getUpperCorner().getY()) / 2;
                        bbox = new double[]{
                                env.getLowerCorner().getX(), env.getLowerCorner().getY(),
                                env.getLowerCorner().getZ(),
                                env.getUpperCorner().getX(), env.getUpperCorner().getY(),
                                env.getUpperCorner().getZ()
                        };
                    } else {
                        bbox = mesh.computeBoundingBox();
                        cx = (bbox[0] + bbox[3]) / 2;
                        cy = (bbox[1] + bbox[4]) / 2;
                    }

                    // Store mesh to sharded disk store (shard selected by featureId)
                    long meshHandle = meshStore.store(mesh, (int) featureId);

                    // Store attributes to disk store
                    long attrOffset = attrStore.store(objectId, featureType, attributes);

                    // Track attribute types incrementally (concurrent)
                    attributeEncoder.trackFieldTypes(attributes);

                    // Spatial metadata stored on disk — zero heap pressure
                    spatialEntryStore.store(
                            new SpatialEntry(featureId, cx, cy, bbox,
                                    meshHandle, attrOffset),
                            (int) featureId);
                }
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
            long totalFeatures = spatialEntryStore.entryCount();
            if (!shouldRun || totalFeatures == 0) {
                return;
            }

            long t0 = System.currentTimeMillis();

            // --- Phase 1: Compute extent by streaming from disk ---
            double[] extent = computeExtentFromStore();

            // Finalize attribute fields from incremental tracking
            List<AttrField> attrFields =
                    attributeEncoder.finalizeFields(totalFeatures);

            // --- Phase 2: Disk-based partitioning (two-pass histogram+scatter) ---
            int targetPerCell = Math.max(formatOptions.getMaxFeaturesPerNode() * 50, 3000);
            int targetCells = Math.max(1, (int) (totalFeatures / targetPerCell));
            int gridDim = Math.max(1, (int) Math.ceil(Math.sqrt(targetCells)));

            PartitionedEntryStore partitioned = PartitionedEntryStore.create(
                    spatialEntryStore, extent, gridDim, tempDir);

            // --- Phase 3+4: Fused cell tree build + merge + flush ---
            // Process cells one at a time: load from disk, build quadtree,
            // remap indices into global tree, flush NodeEntry lists to disk,
            // release cell data. Peak heap = one cell's SpatialEntry list.
            int estimatedNodes = (int) Math.min(
                    (totalFeatures / formatOptions.getMaxFeaturesPerNode()) * 3 + 1,
                    Integer.MAX_VALUE);
            nodeEntryStore = new NodeEntryStore(tempDir, estimatedNodes);

            List<SceneNode> allNodes = new ArrayList<>();
            Set<Integer> meshNodeIndices = new HashSet<>();
            SceneNode globalRoot = new SceneNode(0, 0);
            allNodes.add(globalRoot);
            int nextIndex = 1;

            for (long cellKey : partitioned.cellKeys()) {
                List<SpatialEntry> cellEntries = partitioned.loadCell(cellKey);
                double[] cellExtent = NodeBuilder.computeExtent(cellEntries);
                NodeBuilder.CellTree cellTree =
                        NodeBuilder.buildCellTree(cellEntries, cellExtent,
                                formatOptions.getMaxFeaturesPerNode(),
                                formatOptions.getMaxTreeDepth());

                int offset = nextIndex;
                for (SceneNode node : cellTree.nodes()) {
                    int oldIndex = node.getIndex();
                    int newIndex = oldIndex + offset;

                    List<NodeEntry> entries = cellTree.nodeEntryMap().get(oldIndex);
                    if (entries != null) {
                        nodeEntryStore.writeNode(newIndex, entries);
                        meshNodeIndices.add(newIndex);
                    }

                    node.setIndex(newIndex);
                    allNodes.add(node);
                }

                SceneNode cellRoot = cellTree.nodes().get(0);
                globalRoot.addChild(cellRoot);
                nextIndex += cellTree.nodes().size();
            }

            // Partitioned store no longer needed — free disk space
            partitioned.close();

            NodeBuilder.finalizeGlobalRoot(globalRoot);

            long t1 = System.currentTimeMillis();
            logger.info("Spatial indexing: {} ms ({} nodes, {} with mesh).",
                    t1 - t0, allNodes.size(), meshNodeIndices.size());

            // --- Phase 5: Format-specific output ---
            boolean hasTextures = textureStore.hasTextures();
            writeOutput(allNodes, meshNodeIndices, extent, attrFields, hasTextures);

            long t2 = System.currentTimeMillis();
            logger.info("Node output: {} ms.", t2 - t1);

        } catch (Exception e) {
            throw new WriteException("Failed to write scene layer.", e);
        } finally {
            logger.info("Closing intermediate stores.");
            closeStores();
            logger.info("Deleting intermediate temp directory.");
            deleteDirectoryTree(tempDir);
        }
    }

    // ---- Shared utilities ---------------------------------------------------

    /**
     * Triangulate geometry properties into a mesh. Thread-safe — uses its own
     * PolygonTriangulator instance and operates only on the provided data.
     */
    private static TriangleMesh triangulateGeometries(
            List<GeometryProperty> geometryProperties,
            long featureId,
            PolygonTriangulator triangulator,
            Map<LinearRing, List<TextureCoordinate>> texCoordMap,
            Map<LinearRing, Integer> ringTextureMap) {
        TriangleMesh mesh = new TriangleMesh();

        for (GeometryProperty property : geometryProperties) {
            Geometry<?> geometry = property.getObject();
            if (geometry == null) {
                continue;
            }

            switch (geometry.getGeometryType()) {
                case POLYGON, MULTI_SURFACE, COMPOSITE_SURFACE, SOLID,
                        MULTI_SOLID, COMPOSITE_SOLID, TRIANGULATED_SURFACE -> {
                    TriangleMesh geomMesh = triangulator.triangulate(geometry, featureId,
                            texCoordMap, ringTextureMap);
                    mesh.merge(geomMesh);
                }
                default -> {
                    // Skip non-surface geometry types (points, lines)
                }
            }
        }

        // Post-process: resolve T-junction cracks and remove duplicate triangles
        if (!mesh.isEmpty()) {
            double[] center = mesh.computeCenter();
            double scaleX = GeoTransform.metersPerDegreeLon(center[1]);
            double scaleY = GeoTransform.WGS84_METERS_PER_DEGREE_LAT;
            mesh.resolveTJunctions(scaleX, scaleY, 0.02);
            mesh.removeDuplicateTriangles();
        }

        return mesh;
    }

    /**
     * Shift all vertex Z values so the building's bottom sits at height 0.
     */
    private static void clampMeshToGround(TriangleMesh mesh) {
        List<double[]> positions = mesh.getPositions();
        if (positions.isEmpty()) {
            return;
        }
        double minZ = Double.MAX_VALUE;
        for (double[] pos : positions) {
            if (pos[2] < minZ) minZ = pos[2];
        }
        if (minZ != 0) {
            for (double[] pos : positions) {
                pos[2] -= minZ;
            }
        }
    }

    /**
     * Compute per-texture UV extent from the mesh's triangle texture IDs
     * and vertex UV coordinates. Returns texId → [minU, minV, maxU, maxV].
     */
    protected static Map<Integer, float[]> computeUVExtents(TriangleMesh mesh) {
        Map<Integer, float[]> extents = new HashMap<>();
        List<int[]> triangles = mesh.getTriangles();
        List<Integer> triTexIds = mesh.getTriangleTextureIds();
        List<float[]> texCoords = mesh.getTexCoords();

        for (int t = 0; t < triangles.size(); t++) {
            int texId = triTexIds.get(t);
            if (texId >= 0) {
                float[] ext = extents.computeIfAbsent(texId,
                        k -> new float[]{Float.MAX_VALUE, Float.MAX_VALUE,
                                -Float.MAX_VALUE, -Float.MAX_VALUE});
                int[] tri = triangles.get(t);
                for (int vi : tri) {
                    float[] uv = texCoords.get(vi);
                    ext[0] = Math.min(ext[0], uv[0]);
                    ext[1] = Math.min(ext[1], uv[1]);
                    ext[2] = Math.max(ext[2], uv[0]);
                    ext[3] = Math.max(ext[3], uv[1]);
                }
            }
        }
        return extents;
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
        List<NodeEntry> entries = nodeEntryStore.loadNode(node.getIndex());

        TriangleMesh merged = new TriangleMesh();
        for (NodeEntry entry : entries) {
            merged.merge(meshStore.load(entry.meshHandle()));
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
            Map<Integer, float[]> uvExtents = computeUVExtents(merged);
            atlas = TextureAtlas.build(
                    uniqueTexIds, textureStore, formatOptions.getTextureScale(),
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
            AttributeStore.FeatureAttrs attrs = attrStore.load(entry.attrOffset());
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

    // ---- Private helpers ----------------------------------------------------

    private double[] computeExtentFromStore() {
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, minZ = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;

        Iterator<SpatialEntry> it = spatialEntryStore.iterator();
        while (it.hasNext()) {
            SpatialEntry e = it.next();
            double[] bb = e.bbox();
            if (bb[0] < minX) minX = bb[0];
            if (bb[1] < minY) minY = bb[1];
            if (bb[2] < minZ) minZ = bb[2];
            if (bb[3] > maxX) maxX = bb[3];
            if (bb[4] > maxY) maxY = bb[4];
            if (bb[5] > maxZ) maxZ = bb[5];
        }

        return new double[]{minX, minY, minZ, maxX, maxY, maxZ};
    }

    private void closeStores() {
        if (nodeEntryStore != null) {
            try {
                nodeEntryStore.close();
            } catch (IOException e) {
                logger.warn("Failed to close node entry store: {}", e.getMessage());
            }
        }
        try {
            spatialEntryStore.close();
        } catch (IOException e) {
            logger.warn("Failed to close spatial entry store: {}", e.getMessage());
        }
        try {
            meshStore.close();
        } catch (IOException e) {
            logger.warn("Failed to close mesh store: {}", e.getMessage());
        }
        try {
            attrStore.close();
        } catch (IOException e) {
            logger.warn("Failed to close attribute store: {}", e.getMessage());
        }
        textureStore.close();
    }

    protected static void deleteDirectoryTree(Path root) {
        if (!Files.isDirectory(root)) {
            return;
        }

        List<Path> files = new ArrayList<>();
        List<Path> dirs = new ArrayList<>();
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    files.add(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
                    dirs.add(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException ignored) {
            return;
        }

        files.parallelStream().forEach(file -> {
            try {
                Files.delete(file);
            } catch (IOException ignored) {
                //
            }
        });

        for (Path dir : dirs) {
            try {
                Files.delete(dir);
            } catch (IOException ignored) {
                //
            }
        }
    }
}
