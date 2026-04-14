/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.vis.writer.tiles3d;

import org.citydb.vis.writer.VisWriter;

import org.citydb.core.file.OutputFile;
import org.citydb.io.writer.WriteException;
import org.citydb.io.writer.WriteOptions;
import org.citydb.vis.encoder.tiles3d.GlbEncoder;
import org.citydb.vis.encoder.tiles3d.TilesetSerializer;
import org.citydb.vis.model.AttrField;
import org.citydb.vis.model.FeatureData;
import org.citydb.vis.scene.SceneNode;
import org.citydb.vis.util.FileHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
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
    /** WGS84 semi-major axis (meters). */
    private static final double WGS84_A = 6_378_137.0;
    /** WGS84 first eccentricity squared. */
    private static final double WGS84_E2 = 0.00669437999014;

    private final Logger logger = LoggerFactory.getLogger(Tiles3DWriter.class);
    private final GlbEncoder glbEncoder;
    private final TilesetSerializer tilesetSerializer;

    public Tiles3DWriter(OutputFile outputFile, WriteOptions options) throws WriteException {
        this(validateOutputFile(outputFile, "3D Tiles"),
                loadFormatOptions(options, Tiles3DFormatOptions.class,
                        Tiles3DFormatOptions::new, "3D Tiles"),
                new org.citydb.vis.encoder.AttributeEncoder());
    }

    private Tiles3DWriter(OutputFile outputFile,
                          Tiles3DFormatOptions formatOptions,
                          org.citydb.vis.encoder.AttributeEncoder attributeEncoder) throws WriteException {
        super(outputFile, formatOptions, attributeEncoder);
        this.glbEncoder = new GlbEncoder();
        this.tilesetSerializer = new TilesetSerializer();
    }

    // ---- Format-specific output (Phase 5) -----------------------------------

    @Override
    protected void writeOutput(List<SceneNode> allNodes,
                               Set<Integer> meshNodeIndices,
                               double[] extent,
                               List<AttrField> attrFields,
                               boolean hasTextures) throws IOException {
        Path outputDir = FileHelper.stripExtension(getOutputFile().getFile());
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

        // Parallel: encode GLB per node
        Set<Integer> effectiveMeshIndices = processNodesParallel(allNodes, meshNodeIndices,
                node -> writeNodeGlb(node, tilesDir, attrFields, datasetCenter));

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
                                 List<AttrField> attrFields, double[] datasetCenter)
            throws IOException {
        PreparedNode prepared = prepareNodeMesh(node);

        // Serialize atlas to JPEG bytes for GLB embedding
        byte[] textureBytes = null;
        if (prepared.atlas() != null) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            prepared.atlas().write(baos);
            textureBytes = baos.toByteArray();
        }

        List<FeatureData> featureDataList = loadNodeFeatures(prepared.entries());

        // Encode GLB
        node.setMesh(prepared.mesh());
        byte[] glb = glbEncoder.encode(node, textureBytes, featureDataList, attrFields,
                datasetCenter);
        if (glb == null) {
            return false;
        }

        Files.write(tilesDir.resolve(node.getIndex() + ".glb"), glb);
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

}
