/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.vis.writer;

import org.citydb.core.file.OutputFile;
import org.citydb.vis.I3SFormatOptions;
import org.citydb.vis.geometry.PolygonTriangulator;
import org.citydb.vis.geometry.TriangleMesh;
import org.citydb.vis.scene.I3SNode;
import org.citydb.vis.scene.SceneLayer;
import org.citydb.io.writer.FeatureWriter;
import org.citydb.io.writer.WriteException;
import org.citydb.io.writer.WriteOptions;
import org.citydb.model.appearance.*;
import org.citydb.model.common.ExternalFile;
import org.citydb.model.feature.Feature;
import org.citydb.model.geometry.*;
import org.citydb.model.property.AppearanceProperty;
import org.citydb.model.property.GeometryProperty;
import org.citydb.model.util.GeometryInfo;

import org.citydb.core.concurrent.CountLatch;
import org.citydb.core.concurrent.ExecutorHelper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
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
 *       <li>Spatial metadata → {@link SpatialEntry} queue (~80 bytes/feature in heap)</li>
 *       <li>Attribute types tracked incrementally via {@link I3SAttributeEncoder}</li>
 *     </ul>
 *   </li>
 *   <li><b>Close phase</b> (parallel spatial processing):
 *     <ul>
 *       <li>Adaptive spatial grid partitioning of all features</li>
 *       <li>Per-cell quadtree construction (parallel via {@link I3SNodeBuilder})</li>
 *       <li>Global tree merge with sequential index assignment</li>
 *       <li>Parallel geometry encoding + metadata writing per node</li>
 *     </ul>
 *   </li>
 * </ol>
 * <p>
 * <b>Memory profile for 10M features:</b>
 * <ul>
 *   <li>Heap: ~800 MB (SpatialEntry queue) vs ~6 GB (old FeatureData queue)</li>
 *   <li>Disk: sharded temp files (N × ~12 GB) vs single temp file (~100 GB)</li>
 * </ul>
 * <p>
 * Output coordinate system is hard-coded to EPSG:4326 (WGS84) since
 * Cesium only supports this CRS for I3S layers.
 */
public class I3SWriter implements FeatureWriter {
    private static final int EPSG_4326 = 4326;

    private final OutputFile outputFile;
    private final I3SFormatOptions formatOptions;
    private final Queue<SpatialEntry> spatialEntries;
    private final AtomicLong featureIdCounter;
    private final ExecutorService service;
    private final CountLatch countLatch;
    private final ShardedMeshStore meshStore;
    private final AttributeStore attrStore;
    private final TextureStore textureStore;
    private volatile boolean shouldRun = true;

    private final I3SGeometryEncoder geometryEncoder;
    private final I3SJsonSerializer jsonSerializer;
    private final I3SAttributeEncoder attributeEncoder;

    // Diagnostic counters for texture binding
    private final AtomicLong texDiagHasAppearance = new AtomicLong();
    private final AtomicLong texDiagHasParamTex = new AtomicLong();
    private final AtomicLong texDiagHasTexCoords = new AtomicLong();
    private final AtomicLong texDiagTexCoordMapSize = new AtomicLong();
    private final AtomicLong texDiagMeshHasTC = new AtomicLong();

    public I3SWriter(OutputFile outputFile, WriteOptions options) {
        this.outputFile = outputFile;
        this.formatOptions = getFormatOptions(options);
        this.spatialEntries = new ConcurrentLinkedQueue<>();
        this.featureIdCounter = new AtomicLong(0);

        int cpuCores = Runtime.getRuntime().availableProcessors();
        this.service = ExecutorHelper.newFixedAndBlockingThreadPool(cpuCores, 100);
        this.countLatch = new CountLatch();

        try {
            this.meshStore = new ShardedMeshStore(cpuCores);
            this.attrStore = new AttributeStore();
            this.textureStore = new TextureStore(outputFile);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create disk-backed stores", e);
        }

        this.geometryEncoder = new I3SGeometryEncoder();
        this.jsonSerializer = new I3SJsonSerializer();
        this.attributeEncoder = new I3SAttributeEncoder();
    }

    private I3SFormatOptions getFormatOptions(WriteOptions options) {
        if (options != null) {
            try {
                I3SFormatOptions i3sOptions = options.getFormatOptions()
                        .get(I3SFormatOptions.class);
                if (i3sOptions != null) {
                    return i3sOptions;
                }
            } catch (Exception e) {
                // fall through to default
            }
        }
        return new I3SFormatOptions();
    }

    @Override
    public CompletableFuture<Boolean> write(Feature feature) throws WriteException {
        if (!shouldRun) {
            return CompletableFuture.completedFuture(false);
        }

        // Extract feature metadata on the caller thread (Feature may not be thread-safe)
        long featureId = featureIdCounter.incrementAndGet();
        String objectId = feature.getObjectId().orElse("feature_" + featureId);
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
        int textureId = -1;
        if (feature.hasAppearances()) {
            texDiagHasAppearance.incrementAndGet();
            texCoordMap = new IdentityHashMap<>();
            ringTextureMap = new IdentityHashMap<>();
            for (AppearanceProperty ap : feature.getAppearances().getAll()) {
                Appearance appearance = ap.getObject();
                if (appearance == null) continue;
                for (SurfaceDataProperty sdp : appearance.getSurfaceData()) {
                    SurfaceData<?> sd = sdp.getObject().orElse(null);
                    if (sd instanceof ParameterizedTexture pt) {
                        texDiagHasParamTex.incrementAndGet();
                        if (pt.hasTextureCoordinates()) {
                            texDiagHasTexCoords.incrementAndGet();
                            // Register this PT's texture image
                            int ptTextureId = -1;
                            ExternalFile img = pt.getTextureImage().orElse(null);
                            if (img != null) {
                                ptTextureId = textureStore.register(img);
                                if (textureId < 0) textureId = ptTextureId;
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
            texDiagTexCoordMapSize.addAndGet(texCoordMap.size());
            if (texCoordMap.isEmpty()) {
                texCoordMap = null;
                ringTextureMap = null;
            }
        }

        // Capture the geometry list for the async task
        List<GeometryProperty> geomProps = new ArrayList<>(geometryProperties);
        final Map<LinearRing, List<TextureCoordinate>> finalTexCoordMap = texCoordMap;
        final Map<LinearRing, Integer> finalRingTextureMap = ringTextureMap;
        final int finalTextureId = textureId;
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        countLatch.increment();
        service.execute(() -> {
            try {
                PolygonTriangulator triangulator = new PolygonTriangulator();
                TriangleMesh mesh = triangulateGeometries(geomProps, featureId, triangulator,
                        finalTexCoordMap, finalRingTextureMap);
                if (!mesh.isEmpty()) {
                    if (mesh.hasTexCoords()) texDiagMeshHasTC.incrementAndGet();
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

                    // Only compact spatial metadata stays on heap
                    spatialEntries.add(new SpatialEntry(featureId, cx, cy, bbox,
                            meshHandle, attrOffset, finalTextureId));
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
            if (!shouldRun || spatialEntries.isEmpty()) {
                return;
            }

            // Texture diagnostics
            System.err.println("[I3S-TEX-DIAG] hasAppearance=" + texDiagHasAppearance.get()
                    + " hasParamTex=" + texDiagHasParamTex.get()
                    + " hasTexCoords=" + texDiagHasTexCoords.get()
                    + " texCoordMapEntries=" + texDiagTexCoordMapSize.get()
                    + " meshHasTC=" + texDiagMeshHasTC.get()
                    + " textureStoreCount=" + textureStore.getTextureCount());

            // --- Phase 1: Prepare data ---
            List<SpatialEntry> entries = new ArrayList<>(spatialEntries);
            spatialEntries.clear();
            long totalFeatures = entries.size();

            double[] extent = I3SNodeBuilder.computeExtent(entries);

            // Finalize attribute fields from incremental tracking
            List<I3SAttributeEncoder.AttrField> attrFields =
                    attributeEncoder.finalizeFields(totalFeatures);

            // --- Phase 2: Spatial grid partitioning ---
            Map<Long, List<SpatialEntry>> cellMap = partitionIntoGrid(entries, extent);
            entries = null; // release for GC

            // --- Phase 3: Per-cell quadtree construction (parallel) ---
            List<I3SNodeBuilder.CellTree> cellTrees = cellMap.values().parallelStream()
                    .map(cellEntries -> {
                        double[] cellExtent = I3SNodeBuilder.computeExtent(cellEntries);
                        return I3SNodeBuilder.buildCellTree(cellEntries, cellExtent, formatOptions);
                    })
                    .toList();
            cellMap = null; // release for GC

            // --- Phase 4: Merge into global tree ---
            I3SNodeBuilder.MergedTree merged = I3SNodeBuilder.mergeIntoGlobalTree(cellTrees);
            cellTrees = null; // release for GC

            List<I3SNode> allNodes = merged.allNodes();
            Map<Integer, List<SpatialEntry>> globalEntryMap = merged.nodeEntryMap();
            Set<Integer> meshNodeIndices = merged.meshNodeIndices();

            // --- Phase 5: Write output ---
            boolean hasTextures = textureStore.hasTextures();
            boolean useDds = formatOptions.isGpuTextureCompression();
            SceneLayer sceneLayer = buildSceneLayer(allNodes, extent);
            writeI3SFolder(sceneLayer, allNodes, attrFields, globalEntryMap,
                    meshNodeIndices, hasTextures, useDds);

        } catch (Exception e) {
            throw new WriteException("Failed to write I3S scene layer.", e);
        } finally {
            closeStores();
        }
    }

    /**
     * Partition spatial entries into an adaptive grid. Grid dimensions are
     * computed to target approximately {@code maxFeaturesPerNode * 50} features
     * per cell (at least 3000), balancing between too-many-small-cells and
     * too-few-large-cells for effective parallel quadtree construction.
     */
    private Map<Long, List<SpatialEntry>> partitionIntoGrid(List<SpatialEntry> entries,
                                                            double[] extent) {
        int totalFeatures = entries.size();
        int targetPerCell = Math.max(formatOptions.getMaxFeaturesPerNode() * 50, 3000);
        int targetCells = Math.max(1, totalFeatures / targetPerCell);
        int gridDim = Math.max(1, (int) Math.ceil(Math.sqrt(targetCells)));

        double rangeX = extent[3] - extent[0];
        double rangeY = extent[4] - extent[1];
        double cellWidth = rangeX > 0 ? rangeX / gridDim : 1;
        double cellHeight = rangeY > 0 ? rangeY / gridDim : 1;

        Map<Long, List<SpatialEntry>> cellMap = new HashMap<>();
        for (SpatialEntry entry : entries) {
            int gx = Math.max(0, Math.min((int) ((entry.centerX() - extent[0]) / cellWidth), gridDim - 1));
            int gy = Math.max(0, Math.min((int) ((entry.centerY() - extent[1]) / cellHeight), gridDim - 1));
            long key = (long) gy * gridDim + gx;
            cellMap.computeIfAbsent(key, k -> new ArrayList<>()).add(entry);
        }

        return cellMap;
    }

    /**
     * Triangulate geometry properties into a mesh. Thread-safe — uses its own
     * PolygonTriangulator instance and operates only on the provided data.
     */
    private TriangleMesh triangulateGeometries(List<GeometryProperty> geometryProperties,
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
     */
    private double clampMeshToGround(TriangleMesh mesh) {
        double minZ = Double.MAX_VALUE;
        for (double[] pos : mesh.getPositions()) {
            if (pos[2] < minZ) minZ = pos[2];
        }
        if (minZ != 0 && minZ != Double.MAX_VALUE) {
            for (double[] pos : mesh.getPositions()) {
                pos[2] -= minZ;
            }
        }
        return minZ;
    }

    private SceneLayer buildSceneLayer(List<I3SNode> nodes, double[] extent) {
        SceneLayer layer = new SceneLayer();
        layer.setName("3DCityDB I3S Export");
        layer.setDescription("Exported from 3DCityDB using citydb-tool");
        layer.setNodeCount(nodes.size());
        layer.setExtent(extent);

        if (!nodes.isEmpty() && nodes.get(0).getMbs() != null) {
            layer.setFullExtent(nodes.get(0).getMbs());
        }

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
     */
    private void writeI3SFolder(SceneLayer sceneLayer, List<I3SNode> allNodes,
                                List<I3SAttributeEncoder.AttrField> attrFields,
                                Map<Integer, List<SpatialEntry>> globalEntryMap,
                                Set<Integer> meshNodeIndices,
                                boolean hasTextures, boolean useDds) throws IOException {
        Path outputDir = stripI3sSuffix(outputFile.getFile());
        Path layerDir = outputDir.resolve("layers").resolve("0");
        Files.createDirectories(layerDir);

        // Scene layer JSON
        jsonSerializer.writeSceneLayerJson(layerDir, sceneLayer, attrFields,
                hasTextures, useDds);

        // Identify nodes with feature data
        List<I3SNode> meshNodes = allNodes.stream()
                .filter(n -> meshNodeIndices.contains(n.getIndex()))
                .toList();

        // Parallel: encode geometry + write features/attributes per node.
        // Each node is fully independent — different file paths, no shared mutable state.
        // Limit parallelism to 2 to cap peak memory — Draco encoding requires ~3x the
        // mesh data in memory (source arrays + Vector3[] + encoder internals).
        int geometryParallelism = 2;
        ForkJoinPool geometryPool = new ForkJoinPool(geometryParallelism);
        try {
            geometryPool.submit(() -> meshNodes.parallelStream().forEach(node -> {
                try {
                    int nodeIndex = node.getIndex();
                    List<SpatialEntry> entries = globalEntryMap.get(nodeIndex);

                    // Create output directories for this node
                    Path nodeDir = layerDir.resolve("nodes").resolve(String.valueOf(nodeIndex));
                    Files.createDirectories(nodeDir.resolve("geometries"));
                    Files.createDirectories(nodeDir.resolve("features").resolve("0"));
                    for (int i = 0; i < attrFields.size(); i++) {
                        Files.createDirectories(nodeDir.resolve("attributes").resolve("f_" + i));
                    }

                    // Load meshes from sharded store, merge
                    TriangleMesh merged = new TriangleMesh();
                    for (SpatialEntry entry : entries) {
                        TriangleMesh m = meshStore.load(entry.meshHandle());
                        merged.merge(m);
                    }

                    // Collect unique per-polygon texture IDs from merged mesh
                    Set<Integer> uniqueTexIds = new LinkedHashSet<>();
                    for (int texId : merged.getTriangleTextureIds()) {
                        if (texId >= 0) uniqueTexIds.add(texId);
                    }

                    double texScale = formatOptions.getTextureScale();

                    // Compute UV extents per texture for tiling support
                    Map<Integer, float[]> uvExtents = computeUVExtents(merged);

                    int nodeTextureId = uniqueTexIds.isEmpty() ? -1 : 0;
                    node.setTextureId(nodeTextureId);

                    // Build texture atlas and remap UVs before geometry encoding
                    boolean isAtlas = false;
                    if (uniqueTexIds.size() > 1) {
                        TextureAtlas atlas = TextureAtlas.build(
                                uniqueTexIds, textureStore, texScale,
                                formatOptions.getMaxAtlasSize(), uvExtents);
                        if (atlas != null) {
                            atlas.remapUVs(merged);
                            Path textureDir = nodeDir.resolve("textures");
                            Files.createDirectories(textureDir);
                            atlas.write(textureDir.resolve("0"));
                            if (useDds) {
                                atlas.writeDds(textureDir.resolve("0_0_1"));
                            }
                            isAtlas = true;
                        }
                    } else if (!uniqueTexIds.isEmpty()) {
                        int singleTexId = uniqueTexIds.iterator().next();
                        Path textureDir = nodeDir.resolve("textures");
                        Files.createDirectories(textureDir);
                        textureStore.copyScaled(singleTexId,
                                textureDir.resolve("0"), texScale);
                        if (useDds) {
                            textureStore.copyScaledDds(singleTexId,
                                    textureDir.resolve("0_0_1"), texScale);
                        }
                    }

                    node.setMesh(merged);
                    geometryEncoder.writeNodeGeometry(layerDir, node);

                    List<FeatureData> featureDataList = new ArrayList<>(entries.size());
                    for (SpatialEntry entry : entries) {
                        AttributeStore.FeatureAttrs attrs =
                                attrStore.load(entry.attrOffset());
                        featureDataList.add(new FeatureData(
                                entry.id(), attrs.objectId(), attrs.featureType(),
                                entry.centerX(), entry.centerY(), entry.bbox(),
                                attrs.attributes(), entry.meshHandle(),
                                entry.textureId()));
                    }

                    jsonSerializer.writeNodeFeatures(layerDir, node, featureDataList);
                    attributeEncoder.writeNodeAttributes(layerDir, node, attrFields,
                            featureDataList);

                    if (nodeTextureId >= 0) {
                        jsonSerializer.writeSharedResource(layerDir, node, isAtlas,
                                useDds);
                    }
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            })).join();
        } finally {
            geometryPool.shutdown();
        }

        // Close stores to free temp disk space before writing node pages
        meshStore.close();
        attrStore.close();

        // Node pages AFTER geometry so vertex counts are accurate
        jsonSerializer.writeNodePages(layerDir, allNodes, meshNodeIndices, hasTextures);
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

    private Path stripI3sSuffix(Path path) {
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            return path.resolveSibling(name.substring(0, dot));
        }
        return path;
    }

    private void closeStores() {
        try {
            meshStore.close();
        } catch (IOException ignored) {
        }
        try {
            attrStore.close();
        } catch (IOException ignored) {
        }
        textureStore.close();
    }
}
