/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.vis.writer.i3s;

import org.citydb.vis.writer.VisWriter;

import org.citydb.config.ConfigException;
import org.citydb.core.file.FileType;
import org.citydb.core.file.OutputFile;
import org.citydb.io.writer.WriteException;
import org.citydb.io.writer.WriteOptions;
import org.citydb.vis.encoder.i3s.I3SAttributeEncoder;
import org.citydb.vis.encoder.i3s.I3SGeometryEncoder;
import org.citydb.vis.encoder.i3s.I3SJsonSerializer;
import org.citydb.vis.encoder.TextureAtlas;
import org.citydb.vis.geometry.TriangleMesh;
import org.citydb.vis.model.AttrField;
import org.citydb.vis.model.FeatureData;
import org.citydb.vis.scene.SceneLayer;
import org.citydb.vis.scene.SceneNode;
import org.citydb.vis.store.AttributeStore;
import org.citydb.vis.store.NodeEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Writes city model features to the OGC I3S (Indexed 3D Scene Layer) format.
 * <p>
 * Extends the format-agnostic {@link VisWriter} pipeline with I3S-specific
 * output: Draco-compressed geometry, I3S JSON metadata (scene layer descriptor,
 * node pages, per-node features), binary attribute buffers, and texture files.
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
public class I3SWriter extends VisWriter {
    private static final int EPSG_4326 = 4326;

    /**
     * LOD threshold used by I3S runtimes (CesiumJS) as a screen-space area
     * (px²) above which a node should refine to its children.
     */
    private static final double LEAF_NODE_LOD_THRESHOLD = 131_072;
    private static final double INTERNAL_NODE_LOD_THRESHOLD = 65_536;

    private final Logger logger = LoggerFactory.getLogger(I3SWriter.class);
    private final I3SAttributeEncoder i3sAttributeEncoder;
    private final I3SGeometryEncoder geometryEncoder;
    private final I3SJsonSerializer jsonSerializer;

    public I3SWriter(OutputFile outputFile, WriteOptions options) throws WriteException {
        this(validateOutputFile(outputFile),
                loadFormatOptions(options), new I3SAttributeEncoder());
    }

    private I3SWriter(OutputFile outputFile,
                      I3SFormatOptions formatOptions,
                      I3SAttributeEncoder attributeEncoder) throws WriteException {
        super(outputFile, formatOptions, attributeEncoder);
        this.i3sAttributeEncoder = attributeEncoder;
        this.geometryEncoder = new I3SGeometryEncoder();
        this.jsonSerializer = new I3SJsonSerializer();
    }

    private static OutputFile validateOutputFile(OutputFile file) throws WriteException {
        Objects.requireNonNull(file, "The output file must not be null.");
        if (file.getFileType() == FileType.ARCHIVE) {
            throw new WriteException("I3S export does not support archive output (e.g., .zip, .gz). " +
                    "Specify a regular file path with the .i3s extension.");
        }
        return file;
    }

    private static I3SFormatOptions loadFormatOptions(WriteOptions options) throws WriteException {
        Objects.requireNonNull(options, "The write options must not be null.");
        try {
            return options.getFormatOptions()
                    .getOrElse(I3SFormatOptions.class, I3SFormatOptions::new);
        } catch (ConfigException e) {
            throw new WriteException("Failed to get I3S format options from config.", e);
        }
    }

    // ---- Format-specific output (Phase 5) -----------------------------------

    @Override
    protected void writeOutput(List<SceneNode> allNodes,
                               Set<Integer> meshNodeIndices,
                               double[] extent,
                               List<AttrField> attrFields,
                               boolean hasTextures) throws IOException {
        // Set I3S LOD thresholds on the tree
        setLodThresholds(allNodes);

        SceneLayer sceneLayer = buildSceneLayer(extent);
        writeI3SFolder(sceneLayer, allNodes, attrFields, meshNodeIndices, hasTextures);
    }

    private static void setLodThresholds(List<SceneNode> nodes) {
        for (SceneNode node : nodes) {
            boolean isLeaf = node.getChildren().isEmpty();
            node.setLodThreshold(isLeaf ? LEAF_NODE_LOD_THRESHOLD : INTERNAL_NODE_LOD_THRESHOLD);
        }
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
     */
    private void writeI3SFolder(SceneLayer sceneLayer, List<SceneNode> allNodes,
                                List<AttrField> attrFields,
                                Set<Integer> meshNodeIndices,
                                boolean hasTextures) throws IOException {
        Path outputDir = stripI3sSuffix(getOutputFile().getFile());
        Path layerDir = outputDir.resolve("layers").resolve("0");
        Files.createDirectories(layerDir);

        // Scene layer JSON
        jsonSerializer.writeSceneLayerJson(layerDir, sceneLayer, attrFields,
                hasTextures);

        // Identify nodes with feature data
        List<SceneNode> meshNodes = allNodes.stream()
                .filter(n -> meshNodeIndices.contains(n.getIndex()))
                .toList();

        // Parallel: encode geometry + write features/attributes per node.
        Set<Integer> emptyNodeIndices = ConcurrentHashMap.newKeySet();
        ForkJoinPool geometryPool = new ForkJoinPool(getCpuCores());
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

        // Strip empty nodes from the effective mesh set
        Set<Integer> effectiveMeshIndices = meshNodeIndices;
        if (!emptyNodeIndices.isEmpty()) {
            effectiveMeshIndices = new HashSet<>(meshNodeIndices);
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
     */
    private boolean writeNodeOutput(SceneNode node, Path layerDir,
                                    List<AttrField> attrFields,
                                    AtomicInteger nodesProcessed, int totalMeshNodes)
            throws IOException {
        int nodeIndex = node.getIndex();
        List<NodeEntry> entries = getNodeEntryStore().loadNode(nodeIndex);
        Path nodeDir = layerDir.resolve("nodes").resolve(String.valueOf(nodeIndex));

        // Load meshes from sharded store, merge
        TriangleMesh merged = new TriangleMesh();
        for (NodeEntry entry : entries) {
            TriangleMesh m = getMeshStore().load(entry.meshHandle());
            merged.merge(m);
        }

        // Collect unique per-polygon texture IDs from merged mesh
        Set<Integer> uniqueTexIds = new LinkedHashSet<>();
        for (int texId : merged.getTriangleTextureIds()) {
            if (texId >= 0) uniqueTexIds.add(texId);
        }

        // Invariant: the mesh's UV flag must agree with "some triangle is
        // actually textured".
        if (uniqueTexIds.isEmpty() && merged.hasTexCoords()) {
            merged.setHasTexCoords(false);
        }

        double texScale = getFormatOptions().getTextureScale();

        // Compute UV extents per texture for tiling support
        Map<Integer, float[]> uvExtents = computeUVExtents(merged);

        // Build texture atlas and remap UVs before geometry encoding.
        TextureAtlas atlas = null;
        boolean textured = false;
        if (!uniqueTexIds.isEmpty()) {
            atlas = TextureAtlas.build(
                    uniqueTexIds, getTextureStore(), texScale,
                    getFormatOptions().getMaxAtlasSize(), uvExtents);
            if (atlas != null) {
                atlas.remapUVs(merged);
                textured = true;
            } else {
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
                    getAttrStore().load(entry.attrOffset());
            featureDataList.add(new FeatureData(
                    entry.id(), attrs.objectId(), attrs.featureType(),
                    attrs.attributes()));
        }

        jsonSerializer.writeNodeFeatures(layerDir, node, featureDataList);
        i3sAttributeEncoder.writeNodeAttributes(layerDir, node, attrFields,
                featureDataList);

        int done = nodesProcessed.incrementAndGet();
        if (done % 100 == 0 || done == totalMeshNodes) {
            logger.info("Nodes written: {}/{}.", done, totalMeshNodes);
        }
        return true;
    }

    private static Path stripI3sSuffix(Path path) {
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            return path.resolveSibling(name.substring(0, dot));
        }
        return path;
    }
}
