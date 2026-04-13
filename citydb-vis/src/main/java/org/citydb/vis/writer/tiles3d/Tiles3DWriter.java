/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.vis.writer.tiles3d;

import org.citydb.vis.writer.VisWriter;

import org.citydb.config.ConfigException;
import org.citydb.core.file.FileType;
import org.citydb.core.file.OutputFile;
import org.citydb.io.writer.WriteException;
import org.citydb.io.writer.WriteOptions;
import org.citydb.vis.encoder.TextureAtlas;
import org.citydb.vis.encoder.tiles3d.GlbEncoder;
import org.citydb.vis.encoder.tiles3d.TilesetSerializer;
import org.citydb.vis.geometry.TriangleMesh;
import org.citydb.vis.model.AttrField;
import org.citydb.vis.model.FeatureData;
import org.citydb.vis.scene.SceneNode;
import org.citydb.vis.store.AttributeStore;
import org.citydb.vis.store.NodeEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
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
 * Writes city model features to the OGC 3D Tiles 1.1 format.
 * <p>
 * Extends the format-agnostic {@link VisWriter} pipeline with 3D Tiles output:
 * per-node GLB files (hand-written glTF 2.0 Binary with {@code EXT_mesh_features}
 * and {@code EXT_structural_metadata}), and a multi-level tileset hierarchy
 * ({@code tileset.json} + per-cell {@code subtrees/*.json}).
 * <p>
 * The tileset hierarchy mirrors the I3S node page structure: the root tileset
 * references per-cell sub-tilesets, each containing a quadtree of tiles that
 * are loaded on demand as the viewer zooms in.
 * <p>
 * <b>Coordinate system:</b> all GLB vertex positions are in a local ENU
 * (East-North-Up) frame relative to the dataset center. A single
 * ENU-to-ECEF {@code transform} on the root tile places the entire dataset
 * in the global ECEF coordinate frame. This avoids per-tile transforms and
 * provides sufficient float32 precision for datasets up to ~100 km extent.
 */
public class Tiles3DWriter extends VisWriter {
    private static final double METERS_PER_DEGREE_LAT = 111_320.0;

    /** WGS84 semi-major axis (meters). */
    private static final double WGS84_A = 6_378_137.0;
    /** WGS84 first eccentricity squared. */
    private static final double WGS84_E2 = 0.00669437999014;

    private final Logger logger = LoggerFactory.getLogger(Tiles3DWriter.class);
    private final GlbEncoder glbEncoder;
    private final TilesetSerializer tilesetSerializer;

    public Tiles3DWriter(OutputFile outputFile, WriteOptions options) throws WriteException {
        this(validateOutputFile(outputFile),
                loadFormatOptions(options), new org.citydb.vis.encoder.AttributeEncoder());
    }

    private Tiles3DWriter(OutputFile outputFile,
                          Tiles3DFormatOptions formatOptions,
                          org.citydb.vis.encoder.AttributeEncoder attributeEncoder) throws WriteException {
        super(outputFile, formatOptions, attributeEncoder);
        this.glbEncoder = new GlbEncoder();
        this.tilesetSerializer = new TilesetSerializer();
    }

    private static OutputFile validateOutputFile(OutputFile file) throws WriteException {
        Objects.requireNonNull(file, "The output file must not be null.");
        if (file.getFileType() == FileType.ARCHIVE) {
            throw new WriteException("3D Tiles export does not support archive output (e.g., .zip, .gz). " +
                    "Specify a regular file path with the .3dtiles extension.");
        }
        return file;
    }

    private static Tiles3DFormatOptions loadFormatOptions(WriteOptions options) throws WriteException {
        Objects.requireNonNull(options, "The write options must not be null.");
        try {
            return options.getFormatOptions()
                    .getOrElse(Tiles3DFormatOptions.class, Tiles3DFormatOptions::new);
        } catch (ConfigException e) {
            throw new WriteException("Failed to get 3D Tiles format options from config.", e);
        }
    }

    // ---- Format-specific output (Phase 5) -----------------------------------

    @Override
    protected void writeOutput(List<SceneNode> allNodes,
                               Set<Integer> meshNodeIndices,
                               double[] extent,
                               List<AttrField> attrFields,
                               boolean hasTextures) throws IOException {
        Path outputDir = stripSuffix(getOutputFile().getFile());
        Path tilesDir = outputDir.resolve("tiles");
        Path subtreesDir = outputDir.resolve("subtrees");
        Files.createDirectories(tilesDir);
        Files.createDirectories(subtreesDir);

        // Dataset center for ENU transform
        double[] datasetCenter = {
                (extent[0] + extent[3]) / 2,
                (extent[1] + extent[4]) / 2,
                (extent[2] + extent[5]) / 2
        };

        // Identify mesh nodes
        List<SceneNode> meshNodes = allNodes.stream()
                .filter(n -> meshNodeIndices.contains(n.getIndex()))
                .toList();

        // Parallel: encode GLB per node
        Set<Integer> emptyNodeIndices = ConcurrentHashMap.newKeySet();
        ForkJoinPool pool = new ForkJoinPool(getCpuCores());
        AtomicInteger nodesProcessed = new AtomicInteger(0);
        int totalMeshNodes = meshNodes.size();
        try {
            pool.submit(() -> meshNodes.parallelStream().forEach(node -> {
                try {
                    if (!writeNodeGlb(node, tilesDir, attrFields, datasetCenter,
                            nodesProcessed, totalMeshNodes)) {
                        emptyNodeIndices.add(node.getIndex());
                    }
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            })).join();
        } finally {
            pool.shutdown();
            try {
                pool.awaitTermination(1, TimeUnit.MINUTES);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        // Strip empty nodes
        Set<Integer> effectiveMeshIndices = meshNodeIndices;
        if (!emptyNodeIndices.isEmpty()) {
            effectiveMeshIndices = new HashSet<>(meshNodeIndices);
            effectiveMeshIndices.removeAll(emptyNodeIndices);
            logger.info("Dropped {} empty mesh nodes (all triangles degenerate after welding).",
                    emptyNodeIndices.size());
        }

        // Write sub-tilesets (tree-based spatial split)
        SceneNode globalRoot = allNodes.get(0);
        List<SceneNode> cellRoots = globalRoot.getChildren();
        AtomicInteger subtreeCounter = new AtomicInteger(cellRoots.size());
        for (int i = 0; i < cellRoots.size(); i++) {
            Path subtreeFile = subtreesDir.resolve(i + ".json");
            tilesetSerializer.writeSubTileset(subtreeFile, cellRoots.get(i),
                    effectiveMeshIndices, subtreeCounter);
        }

        // Write root tileset.json
        double[] transform = computeEnuToEcefTransform(datasetCenter);
        tilesetSerializer.writeRootTileset(outputDir, globalRoot, effectiveMeshIndices,
                extent, attrFields, transform);

        logger.info("3D Tiles output: {} tiles, {} sub-tileset files, tileset.json written.",
                effectiveMeshIndices.size(), subtreeCounter.get());
    }

    /**
     * Process a single mesh node: merge meshes, build texture atlas, encode GLB.
     */
    private boolean writeNodeGlb(SceneNode node, Path tilesDir,
                                 List<AttrField> attrFields, double[] datasetCenter,
                                 AtomicInteger nodesProcessed, int totalMeshNodes)
            throws IOException {
        int nodeIndex = node.getIndex();
        List<NodeEntry> entries = getNodeEntryStore().loadNode(nodeIndex);

        // Merge meshes from sharded store
        TriangleMesh merged = new TriangleMesh();
        for (NodeEntry entry : entries) {
            TriangleMesh m = getMeshStore().load(entry.meshHandle());
            merged.merge(m);
        }

        // Collect unique texture IDs
        Set<Integer> uniqueTexIds = new LinkedHashSet<>();
        for (int texId : merged.getTriangleTextureIds()) {
            if (texId >= 0) uniqueTexIds.add(texId);
        }

        if (uniqueTexIds.isEmpty() && merged.hasTexCoords()) {
            merged.setHasTexCoords(false);
        }

        double texScale = getFormatOptions().getTextureScale();
        Map<Integer, float[]> uvExtents = computeUVExtents(merged);

        // Build texture atlas
        TextureAtlas atlas = null;
        byte[] textureBytes = null;
        if (!uniqueTexIds.isEmpty()) {
            atlas = TextureAtlas.build(
                    uniqueTexIds, getTextureStore(), texScale,
                    getFormatOptions().getMaxAtlasSize(), uvExtents);
            if (atlas != null) {
                atlas.remapUVs(merged);
                // Serialize atlas to JPEG bytes for GLB embedding
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                atlas.write(baos);
                textureBytes = baos.toByteArray();
            } else {
                logger.warn("Node {}: all referenced textures failed to load, " +
                        "falling back to untextured rendering.", nodeIndex);
                merged.setHasTexCoords(false);
            }
        }
        node.setTextureId(textureBytes != null ? 0 : -1);

        // Load per-feature attribute data
        List<FeatureData> featureDataList = new ArrayList<>(entries.size());
        for (NodeEntry entry : entries) {
            AttributeStore.FeatureAttrs attrs = getAttrStore().load(entry.attrOffset());
            featureDataList.add(new FeatureData(
                    entry.id(), attrs.objectId(), attrs.featureType(),
                    attrs.attributes()));
        }

        // Encode GLB
        node.setMesh(merged);
        byte[] glb = glbEncoder.encode(node, textureBytes, featureDataList, attrFields,
                datasetCenter);

        if (glb == null) {
            int done = nodesProcessed.incrementAndGet();
            if (done % 100 == 0 || done == totalMeshNodes) {
                logger.info("Nodes written: {}/{}.", done, totalMeshNodes);
            }
            return false;
        }

        // Write GLB file
        Files.write(tilesDir.resolve(nodeIndex + ".glb"), glb);

        int done = nodesProcessed.incrementAndGet();
        if (done % 100 == 0 || done == totalMeshNodes) {
            logger.info("Nodes written: {}/{}.", done, totalMeshNodes);
        }
        return true;
    }

    // ---- ENU-to-ECEF transform ------------------------------------------

    /**
     * Compute the 4x4 ENU-to-ECEF transform matrix at the given geographic
     * center, in column-major order (as required by 3D Tiles / glTF).
     */
    static double[] computeEnuToEcefTransform(double[] center) {
        double lonRad = Math.toRadians(center[0]);
        double latRad = Math.toRadians(center[1]);
        double alt = center[2];

        double sinLon = Math.sin(lonRad);
        double cosLon = Math.cos(lonRad);
        double sinLat = Math.sin(latRad);
        double cosLat = Math.cos(latRad);

        // Prime vertical radius of curvature
        double N = WGS84_A / Math.sqrt(1 - WGS84_E2 * sinLat * sinLat);

        // ECEF position
        double X = (N + alt) * cosLat * cosLon;
        double Y = (N + alt) * cosLat * sinLon;
        double Z = (N * (1 - WGS84_E2) + alt) * sinLat;

        // Column-major 4x4: columns are East, North, Up, Translation
        return new double[]{
                -sinLon, cosLon, 0, 0,
                -sinLat * cosLon, -sinLat * sinLon, cosLat, 0,
                cosLat * cosLon, cosLat * sinLon, sinLat, 0,
                X, Y, Z, 1
        };
    }

    private static Path stripSuffix(Path path) {
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            return path.resolveSibling(name.substring(0, dot));
        }
        return path;
    }
}
