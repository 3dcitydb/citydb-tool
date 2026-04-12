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
import org.citydb.vis.I3SFormatOptions;
import org.citydb.vis.model.AttrField;
import org.citydb.vis.model.FeatureData;
import org.citydb.vis.encoder.I3SAttributeEncoder;
import org.citydb.vis.encoder.I3SGeometryEncoder;
import org.citydb.vis.encoder.I3SJsonSerializer;
import org.citydb.vis.encoder.TextureAtlas;
import org.citydb.vis.geometry.PolygonTriangulator;
import org.citydb.vis.geometry.TriangleMesh;
import org.citydb.vis.scene.I3SNode;
import org.citydb.vis.scene.SceneLayer;
import org.citydb.vis.store.AttributeStore;
import org.citydb.vis.store.NodeEntry;
import org.citydb.vis.store.NodeEntryStore;
import org.citydb.vis.store.PartitionedEntryStore;
import org.citydb.vis.store.ShardedMeshStore;
import org.citydb.vis.store.SpatialEntry;
import org.citydb.vis.store.SpatialEntryStore;
import org.citydb.vis.store.TextureStore;
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

/**
 * Writes city model features to the OGC I3S (Indexed 3D Scene Layer) format
 * using a segmented processing architecture optimized for tens of millions
 * of features.
 * <p>
 * <b>Architecture overview:</b>
 * <ol>
 *   <li><b>Write phase</b> (parallel, memory-efficient):
 *     <ul>
 *       <li>Triangle meshes → {@link ShardedMeshStore} (lock-free sharded disk storage)</li>
 *       <li>Attributes → {@link AttributeStore} (disk-backed, off-heap)</li>
 *       <li>Spatial metadata → {@link SpatialEntry} on disk via {@link SpatialEntryStore} (off-heap)</li>
 *       <li>Attribute types tracked incrementally via {@link I3SAttributeEncoder}</li>
 *     </ul>
 *   </li>
 *   <li><b>Close phase</b> (disk-backed spatial processing):
 *     <ul>
 *       <li>Disk-based grid partitioning via {@link PartitionedEntryStore}</li>
 *       <li>Per-cell quadtree construction + flush to {@link NodeEntryStore}</li>
 *       <li>Parallel geometry encoding + metadata writing per node</li>
 *     </ul>
 *   </li>
 * </ol>
 * <p>
 * <b>Memory profile (100M features, default settings):</b>
 * <ul>
 *   <li>Heap: ~0 during write phase; ~500 MB peak during close phase
 *       (I3SNode tree + index arrays). All entry data is streamed from
 *       disk per-cell and flushed to {@link NodeEntryStore} immediately
 *       after quadtree construction.</li>
 *   <li>Disk: spatial entry shards + partitioned file + node entry file +
 *       mesh shards + attribute store (total ~20 GB at 100M features).</li>
 * </ul>
 * <p>
 * <b>Coordinate system:</b> the current implementation hard-codes the output
 * CRS to EPSG:4326 (WGS 84, lon/lat/ellipsoid-height). This is a simplification
 * of the writer, not a limitation of the I3S specification — I3S allows any
 * valid {@code spatialReference} (geographic or projected). The choice is
 * driven by two practical constraints:
 * <ul>
 *   <li>CesiumJS's I3S loader is most mature on the global / geographic path
 *       (ENU-to-ECEF at each node center); projected CRS support is less
 *       well-trodden.</li>
 *   <li>All internal math in this module — ENU-to-ECEF normal rotation in
 *       {@link I3SGeometryEncoder}, the {@code 111320·cos(lat)} degree-to-meter
 *       conversions in {@link org.citydb.vis.geometry.PolygonTriangulator} and
 *       {@link org.citydb.vis.scene.BoundingVolume}, and the Draco position
 *       quantization with {@code i3s-scale_x/y} metadata — assumes
 *       {@code X=longitude°, Y=latitude°, Z=meters}.</li>
 * </ul>
 * Supporting other CRS would require revisiting every site above, not just
 * changing the declared {@code wkid}.
 */
public class I3SWriter implements FeatureWriter {
    private static final int EPSG_4326 = 4326;
    private static final String TEMP_DIR_NAME = ".tmp";
    private final Logger logger = LoggerFactory.getLogger(I3SWriter.class);

    private final OutputFile outputFile;
    private final I3SFormatOptions formatOptions;
    private final SpatialEntryStore spatialEntryStore;
    private final AtomicLong featureIdCounter;
    private final int cpuCores;
    private final ExecutorService service;
    private final CountLatch countLatch;
    private final Path tempDir;
    private final ShardedMeshStore meshStore;
    private final AttributeStore attrStore;
    private final TextureStore textureStore;
    private final I3SGeometryEncoder geometryEncoder;
    private final I3SJsonSerializer jsonSerializer;
    private final I3SAttributeEncoder attributeEncoder;

    private NodeEntryStore nodeEntryStore;
    private volatile boolean shouldRun = true;

    public I3SWriter(OutputFile outputFile, WriteOptions options) throws WriteException {
        Objects.requireNonNull(outputFile, "The output file must not be null.");
        Objects.requireNonNull(options, "The write options must not be null.");

        // I3S writes a directory tree of geometry, attribute, and texture files
        // via raw filesystem operations and cannot be packed into a single
        // archive stream. Reject archive outputs (.zip, .gz) early so the user
        // gets a clear error instead of a half-written broken folder.
        if (outputFile.getFileType() == FileType.ARCHIVE) {
            throw new WriteException("I3S export does not support archive output (e.g., .zip, .gz). " +
                    "Specify a regular file path with the .i3s extension.");
        }

        this.outputFile = outputFile;
        this.featureIdCounter = new AtomicLong(0);

        try {
            this.formatOptions = options.getFormatOptions()
                    .getOrElse(I3SFormatOptions.class, I3SFormatOptions::new);
        } catch (ConfigException e) {
            throw new WriteException("Failed to get I3S format options from config.", e);
        }

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

        this.geometryEncoder = new I3SGeometryEncoder();
        this.jsonSerializer = new I3SJsonSerializer();
        this.attributeEncoder = new I3SAttributeEncoder();
    }

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
                        double minZ = clampMeshToGround(mesh);
                        if (env != null) {
                            env = Envelope.of(
                                    Coordinate.of(env.getLowerCorner().getX(),
                                            env.getLowerCorner().getY(),
                                            env.getLowerCorner().getZ() - minZ),
                                    Coordinate.of(env.getUpperCorner().getX(),
                                            env.getUpperCorner().getY(),
                                            env.getUpperCorner().getZ() - minZ));
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

            List<I3SNode> allNodes = new ArrayList<>();
            Set<Integer> meshNodeIndices = new HashSet<>();
            I3SNode globalRoot = new I3SNode(0, 0);
            allNodes.add(globalRoot);
            int nextIndex = 1;

            for (long cellKey : partitioned.cellKeys()) {
                List<SpatialEntry> cellEntries = partitioned.loadCell(cellKey);
                double[] cellExtent = I3SNodeBuilder.computeExtent(cellEntries);
                I3SNodeBuilder.CellTree cellTree =
                        I3SNodeBuilder.buildCellTree(cellEntries, cellExtent, formatOptions);

                int offset = nextIndex;
                for (I3SNode node : cellTree.nodes()) {
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

                I3SNode cellRoot = cellTree.nodes().get(0);
                globalRoot.addChild(cellRoot);
                nextIndex += cellTree.nodes().size();
            }

            // Partitioned store no longer needed — free disk space
            partitioned.close();

            I3SNodeBuilder.finalizeGlobalRoot(globalRoot);

            long t1 = System.currentTimeMillis();
            logger.info("Spatial indexing: {} ms ({} nodes, {} with mesh).",
                    t1 - t0, allNodes.size(), meshNodeIndices.size());

            // --- Phase 5: Write output (reads per-node from NodeEntryStore) ---
            boolean hasTextures = textureStore.hasTextures();
            SceneLayer sceneLayer = buildSceneLayer(extent);
            writeI3SFolder(sceneLayer, allNodes, attrFields,
                    meshNodeIndices, hasTextures);

            long t2 = System.currentTimeMillis();
            logger.info("Node output: {} ms.", t2 - t1);

        } catch (Exception e) {
            throw new WriteException("Failed to write I3S scene layer.", e);
        } finally {
            logger.info("Closing intermediate stores.");
            closeStores();
            logger.info("Deleting intermediate temp directory.");
            deleteDirectoryTree(tempDir);
        }
    }

    /**
     * Compute the global extent by streaming all entries from the disk store.
     * Only accumulator variables are held on heap — entries are discarded
     * after each chunk.
     */
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


    /**
     * Triangulate geometry properties into a mesh. Thread-safe — uses its own
     * PolygonTriangulator instance and operates only on the provided data.
     */
    private static TriangleMesh triangulateGeometries(List<GeometryProperty> geometryProperties,
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
            double scaleX = 111_320.0 * Math.cos(Math.toRadians(center[1]));
            double scaleY = 111_320.0;
            mesh.resolveTJunctions(scaleX, scaleY, 0.02);
            mesh.removeDuplicateTriangles();
        }

        return mesh;
    }

    /**
     * Shift all vertex Z values so the building's bottom sits at height 0.
     * Returns 0 when the mesh has no positions (nothing to clamp).
     */
    private static double clampMeshToGround(TriangleMesh mesh) {
        List<double[]> positions = mesh.getPositions();
        if (positions.isEmpty()) {
            return 0;
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
        return minZ;
    }

    private static SceneLayer buildSceneLayer(double[] extent) {
        SceneLayer layer = new SceneLayer();
        layer.setName("3DCityDB I3S Export");
        layer.setDescription("Exported from 3DCityDB using citydb-tool");
        layer.setExtent(extent);
        layer.setWkid(EPSG_4326);
        return layer;
    }

    /**
     * Write I3S folder structure with fully parallel node processing.
     * <p>
     * Each mesh node is processed independently in a parallel stream:
     * load meshes from {@link ShardedMeshStore}, merge, encode geometry,
     * reconstruct {@link FeatureData} from {@link AttributeStore}, write
     * features and attributes. This parallelizes the entire node pipeline
     * instead of just the metadata phase.
     * <p>
     * A node whose mesh becomes empty after vertex welding / degenerate
     * triangle removal is dropped from the effective mesh set before the
     * node pages are written, so the node page will not reference a missing
     * geometry file.
     */
    private void writeI3SFolder(SceneLayer sceneLayer, List<I3SNode> allNodes,
                                List<AttrField> attrFields,
                                Set<Integer> meshNodeIndices,
                                boolean hasTextures) throws IOException {
        Path outputDir = stripI3sSuffix(outputFile.getFile());
        Path layerDir = outputDir.resolve("layers").resolve("0");
        Files.createDirectories(layerDir);

        // Scene layer JSON
        jsonSerializer.writeSceneLayerJson(layerDir, sceneLayer, attrFields,
                hasTextures);

        // Identify nodes with feature data
        List<I3SNode> meshNodes = allNodes.stream()
                .filter(n -> meshNodeIndices.contains(n.getIndex()))
                .toList();

        // Parallel: encode geometry + write features/attributes per node.
        // Each node is fully independent — different file paths, no shared mutable state.
        // Track nodes whose geometry ended up empty so they can be removed from
        // meshNodeIndices before node pages are written.
        Set<Integer> emptyNodeIndices = ConcurrentHashMap.newKeySet();
        ForkJoinPool geometryPool = new ForkJoinPool(cpuCores);
        AtomicInteger nodesProcessed = new AtomicInteger(0);
        int totalMeshNodes = meshNodes.size();
        try {
            geometryPool.submit(() -> meshNodes.parallelStream().forEach(node -> {
                try {
                    if (!writeNodeOutput(node, layerDir, attrFields,
                            nodesProcessed, totalMeshNodes)) {
                        emptyNodeIndices.add(node.getIndex());
                    }
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            })).join();
        } finally {
            geometryPool.shutdown();
            try {
                geometryPool.awaitTermination(1, TimeUnit.MINUTES);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        // Strip empty nodes from the effective mesh set so the node pages
        // match what was actually written to disk.
        Set<Integer> effectiveMeshIndices = meshNodeIndices;
        if (!emptyNodeIndices.isEmpty()) {
            effectiveMeshIndices = new java.util.HashSet<>(meshNodeIndices);
            effectiveMeshIndices.removeAll(emptyNodeIndices);
            logger.info("Dropped {} empty mesh nodes (all triangles degenerate after welding).",
                    emptyNodeIndices.size());
        }

        // Node pages AFTER geometry so vertex counts are accurate
        jsonSerializer.writeNodePages(layerDir, allNodes, effectiveMeshIndices, hasTextures);
    }

    /**
     * Process a single mesh node end-to-end: merge meshes from the sharded
     * store, build the texture atlas (if any), encode Draco geometry, and
     * write per-node feature/attribute JSON files.
     * <p>
     * All I/O targets paths that are unique to this node, so the method is
     * safe to call concurrently from a parallel stream.
     *
     * @return {@code true} if the node was written with non-empty geometry,
     *         {@code false} if welding/degenerate filtering collapsed the
     *         mesh to zero triangles (caller must drop this node from the
     *         effective mesh set before writing node pages).
     */
    private boolean writeNodeOutput(I3SNode node, Path layerDir,
                                    List<AttrField> attrFields,
                                    AtomicInteger nodesProcessed, int totalMeshNodes)
            throws IOException {
        int nodeIndex = node.getIndex();
        List<NodeEntry> entries = nodeEntryStore.loadNode(nodeIndex);
        Path nodeDir = layerDir.resolve("nodes").resolve(String.valueOf(nodeIndex));

        // Load meshes from sharded store, merge
        TriangleMesh merged = new TriangleMesh();
        for (NodeEntry entry : entries) {
            TriangleMesh m = meshStore.load(entry.meshHandle());
            merged.merge(m);
        }

        // Collect unique per-polygon texture IDs from merged mesh
        Set<Integer> uniqueTexIds = new LinkedHashSet<>();
        for (int texId : merged.getTriangleTextureIds()) {
            if (texId >= 0) uniqueTexIds.add(texId);
        }

        // Invariant: the mesh's UV flag must agree with "some triangle is
        // actually textured". It is possible for PolygonTriangulator to emit
        // UV-carrying vertices whose triangles have textureId = -1 (e.g. a
        // ParameterizedTexture with coordinates but no texture image). In
        // that case we would otherwise encode `uv0` into Draco while the
        // node page declares the untextured geometry definition, producing
        // a compressedAttributes / geometryDefinitions mismatch that
        // CesiumJS cannot decode.
        if (uniqueTexIds.isEmpty() && merged.hasTexCoords()) {
            merged.setHasTexCoords(false);
        }

        double texScale = formatOptions.getTextureScale();

        // Compute UV extents per texture for tiling support
        Map<Integer, float[]> uvExtents = computeUVExtents(merged);

        // Build texture atlas and remap UVs before geometry encoding.
        // Unified path: single- and multi-texture nodes both go through
        // TextureAtlas so that UV tiling / wrapping is normalized consistently.
        // NOTE: we remap UVs now (encoder reads them), but defer the image
        // file write until after hasGeometry is confirmed — welding may still
        // collapse the mesh to empty, in which case we must not leave an
        // orphan texture file on disk.
        TextureAtlas atlas = null;
        boolean textured = false;
        if (!uniqueTexIds.isEmpty()) {
            atlas = TextureAtlas.build(
                    uniqueTexIds, textureStore, texScale,
                    formatOptions.getMaxAtlasSize(), uvExtents);
            if (atlas != null) {
                atlas.remapUVs(merged);
                textured = true;
            } else {
                // All referenced textures failed to load. Downgrade the node
                // to untextured so the node page, geometryDefinition, and
                // Draco compressedAttributes all stay consistent. Dropping
                // the UV flag on the mesh also tells the encoder to emit
                // `[position, normal, feature-index]` instead of `uv0`.
                logger.warn("Node {}: all referenced textures failed to load, " +
                        "falling back to untextured rendering.", nodeIndex);
                merged.setHasTexCoords(false);
            }
        }
        node.setTextureId(textured ? 0 : -1);

        node.setMesh(merged);
        boolean hasGeometry = geometryEncoder.writeNodeGeometry(layerDir, node);
        if (!hasGeometry) {
            int done = nodesProcessed.incrementAndGet();
            if (done % 100 == 0 || done == totalMeshNodes) {
                logger.info("Nodes written: {}/{}.", done, totalMeshNodes);
            }
            return false;
        }

        // Geometry confirmed — now safe to materialize the atlas file.
        if (atlas != null) {
            Path textureDir = nodeDir.resolve("textures");
            Files.createDirectories(textureDir);
            atlas.write(textureDir.resolve("0"));
        }

        List<FeatureData> featureDataList = new ArrayList<>(entries.size());
        for (NodeEntry entry : entries) {
            AttributeStore.FeatureAttrs attrs =
                    attrStore.load(entry.attrOffset());
            featureDataList.add(new FeatureData(
                    entry.id(), attrs.objectId(), attrs.featureType(),
                    attrs.attributes()));
        }

        jsonSerializer.writeNodeFeatures(layerDir, node, featureDataList);
        attributeEncoder.writeNodeAttributes(layerDir, node, attrFields,
                featureDataList);

        int done = nodesProcessed.incrementAndGet();
        if (done % 100 == 0 || done == totalMeshNodes) {
            logger.info("Nodes written: {}/{}.", done, totalMeshNodes);
        }
        return true;
    }

    /**
     * Compute per-texture UV extent from the mesh's triangle texture IDs
     * and vertex UV coordinates. Returns texId → [minU, minV, maxU, maxV].
     */
    private static Map<Integer, float[]> computeUVExtents(TriangleMesh mesh) {
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

    private static Path stripI3sSuffix(Path path) {
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            return path.resolveSibling(name.substring(0, dot));
        }
        return path;
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

    private static void deleteDirectoryTree(Path root) {
        if (!Files.isDirectory(root)) {
            return;
        }

        // Collect all entries in a single walk: files first, directories in
        // postVisit order so they can be removed bottom-up after their contents.
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

        // Parallelize at file granularity so the work scales with file count
        // instead of with the number of top-level bucket subdirectories.
        files.parallelStream().forEach(file -> {
            try {
                Files.delete(file);
            } catch (IOException ignored) {
                //
            }
        });

        // Few directories compared to files; remove them sequentially bottom-up.
        for (Path dir : dirs) {
            try {
                Files.delete(dir);
            } catch (IOException ignored) {
                //
            }
        }
    }
}
