/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences (HFT Stuttgart) <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.writer.tiles3d;

import org.citydb.vis.writer.VisWriter;

import org.citydb.core.file.OutputFile;
import org.citydb.io.writer.WriteException;
import org.citydb.io.writer.WriteOptions;
import org.citydb.vis.pipeline.PipelineContext;
import org.citydb.vis.VisExportException;
import org.citydb.vis.encoder.TextureAtlas;
import org.citydb.vis.encoder.tiles3d.CellAggregator;
import org.citydb.vis.encoder.tiles3d.GlbEncoder;
import org.citydb.vis.encoder.tiles3d.TilePaths;
import org.citydb.vis.encoder.tiles3d.TilesetSerializer;
import org.citydb.vis.model.AttrField;
import org.citydb.vis.model.FeatureData;
import org.citydb.vis.scene.SceneNode;
import org.citydb.vis.util.FileHelper;
import org.citydb.vis.util.GeoTransform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Writes city model features to the OGC 3D Tiles 1.1 format.
 * <p>
 * Extends the format-agnostic {@link VisWriter} pipeline with 3D Tiles output:
 * per-node GLB files (hand-written glTF 2.0 Binary with {@code EXT_mesh_features}
 * and {@code EXT_structural_metadata}), and a multi-level tileset hierarchy
 * ({@code tileset.json} + {@code subtrees/*.json}).
 * <p>
 * The tileset hierarchy lazy-loads the scene via three nested layers: a
 * spatial aggregation tree grouping populated grid cells (see
 * {@link CellAggregator}), per-cell quadtrees under each aggregation leaf,
 * and tree-based sub-tileset splitting so no single JSON exceeds
 * {@link TilesetSerializer#MAX_NODES_PER_SUBTILESET} nodes. This keeps the
 * root tileset small even for city-scale datasets with thousands of cells.
 * <p>
 * <b>Coordinate system:</b> all GLB vertex positions are in a local ENU
 * (East-North-Up) frame relative to the dataset center. A single
 * ENU-to-ECEF {@code transform} on the root tile places the entire dataset
 * in the global ECEF coordinate frame. This avoids per-tile transforms and
 * provides sufficient float32 precision for datasets up to ~100 km extent.
 */
public class Tiles3DWriter extends VisWriter {
    private final Logger logger = LoggerFactory.getLogger(Tiles3DWriter.class);
    private final GlbEncoder glbEncoder;
    private final TilesetSerializer tilesetSerializer;

    public Tiles3DWriter(OutputFile outputFile, WriteOptions options) throws WriteException {
        this(validateOutputFile(outputFile, "3D Tiles"),
                loadFormatOptions(options, Tiles3DFormatOptions.class,
                        Tiles3DFormatOptions::new, "3D Tiles"),
                new org.citydb.vis.encoder.AttributeEncoder(),
                options);
    }

    private Tiles3DWriter(OutputFile outputFile,
                          Tiles3DFormatOptions formatOptions,
                          org.citydb.vis.encoder.AttributeEncoder attributeEncoder,
                          WriteOptions writeOptions) throws WriteException {
        super(outputFile, formatOptions, attributeEncoder, writeOptions);
        this.glbEncoder = new GlbEncoder();
        this.tilesetSerializer = new TilesetSerializer();
    }

    // ---- Format-specific output (Phase 5) -----------------------------------

    @Override
    protected void writeOutput(PipelineContext ctx) throws VisExportException {
        List<SceneNode> allNodes = ctx.allNodes();
        Set<Integer> meshNodeIndices = ctx.meshNodeIndices();
        Map<Integer, int[]> cellRootGridCoords = ctx.cellRootGridCoords();
        double[] extent = ctx.extent();
        List<AttrField> attrFields = ctx.attrFields();
        Path outputDir = FileHelper.stripExtension(getOutputFile().getFile());
        Path tilesDir = outputDir.resolve("tiles");
        Path subtreesDir = outputDir.resolve("subtrees");
        try {
            Files.createDirectories(tilesDir);
            Files.createDirectories(subtreesDir);
        } catch (IOException e) {
            throw new VisExportException("Failed to create 3D Tiles output directories.", e);
        }

        // Dataset center for ENU transform
        double[] datasetCenter = {
                (extent[0] + extent[3]) / 2,
                (extent[1] + extent[4]) / 2,
                (extent[2] + extent[5]) / 2
        };

        // Wrap cell roots in a spatial aggregation tree before path assignment.
        // Without this, the root tileset would carry one child per populated
        // grid cell (up to gridDim^2 entries for city-scale datasets); with
        // it, the root is a single external ref and deeper layers split via
        // TilesetSerializer.MAX_NODES_PER_SUBTILESET regardless of cell count.
        SceneNode globalRoot = allNodes.get(0);
        AtomicInteger aggIndexer = new AtomicInteger(allNodes.size());
        SceneNode aggRoot = CellAggregator.build(globalRoot.getChildren(),
                cellRootGridCoords, aggIndexer);
        Map<Integer, int[]> tilePaths = TilePaths.buildPathIndex(aggRoot);

        // Pre-create parent directories serially so the parallel GLB
        // writer below doesn't contend on filesystem stat syscalls.
        try {
            createTileParentDirs(tilesDir, tilePaths, meshNodeIndices);
        } catch (IOException e) {
            throw new VisExportException("Failed to create tile output directories.", e);
        }

        // Parallel: encode GLB per node
        Set<Integer> effectiveMeshIndices = processNodesParallel(allNodes, meshNodeIndices,
                node -> writeNodeGlb(node, tilesDir, tilePaths, attrFields, datasetCenter));

        try {
            // Write sub-tilesets (tree-based spatial split) starting at the
            // aggregation root; the writer recurses through aggregation nodes
            // and per-cell quadtrees uniformly.
            AtomicInteger subtreeFileCount = new AtomicInteger();
            tilesetSerializer.writeSubTileset(subtreesDir, aggRoot,
                    effectiveMeshIndices, tilePaths, subtreeFileCount);

            // Write root tileset.json: a single external ref to the aggregation
            // root's subtree file keeps the root bounded independent of cell count.
            double[] transform = GeoTransform.enuToEcefMatrix(datasetCenter);
            tilesetSerializer.writeRootTileset(outputDir, globalRoot, aggRoot,
                    extent, attrFields, transform, tilePaths);

            logger.info("3D Tiles output: {} tiles, {} sub-tileset files, tileset.json written.",
                    effectiveMeshIndices.size(), subtreeFileCount.get());
        } catch (IOException e) {
            throw new VisExportException("Failed to write 3D Tiles tileset.", e);
        }
    }

    /**
     * Process a single mesh node: merge meshes, build texture atlas, encode GLB.
     */
    private boolean writeNodeGlb(SceneNode node, Path tilesDir,
                                 Map<Integer, int[]> tilePaths,
                                 List<AttrField> attrFields, double[] datasetCenter)
            throws VisExportException {
        PreparedNode prepared = prepareNodeMesh(node, true);
        List<FeatureData> featureDataList = loadNodeFeatures(prepared.entries());

        try {
            // Serialize each atlas page to JPEG bytes and index texture ids by
            // page so the GLB encoder can route textured triangles to the
            // primitive backed by the correct atlas.
            List<byte[]> atlasBytesList = new ArrayList<>(prepared.atlases().size());
            Map<Integer, Integer> texIdToPage = new HashMap<>();
            for (int p = 0; p < prepared.atlases().size(); p++) {
                TextureAtlas atlas = prepared.atlases().get(p);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                atlas.write(baos);
                atlasBytesList.add(baos.toByteArray());
                for (int texId : atlas.getTextureIds()) {
                    texIdToPage.put(texId, p);
                }
            }

            // Encode GLB
            node.setMesh(prepared.mesh());
            byte[] glb = glbEncoder.encode(node, atlasBytesList, texIdToPage,
                    featureDataList, attrFields, datasetCenter);
            if (glb == null) {
                return false;
            }

            Files.write(tilesDir.resolve(TilePaths.tileFile(tilePaths.get(node.getIndex()))), glb);
            return true;
        } catch (IOException e) {
            throw new VisExportException("Failed to write 3D Tiles node " + node.getIndex() + ".", e);
        }
    }

    private static void createTileParentDirs(Path tilesDir,
                                             Map<Integer, int[]> tilePaths,
                                             Set<Integer> meshNodeIndices) throws IOException {
        Set<String> dirs = new HashSet<>();
        for (int idx : meshNodeIndices) {
            int[] path = tilePaths.get(idx);
            if (path.length >= 2) {
                dirs.add(TilePaths.parentDir(path));
            }
        }
        // Create shallow directories first so each deeper createDirectories
        // call finds its ancestors already present (fewer redundant stats).
        List<String> sorted = new ArrayList<>(dirs);
        sorted.sort(Comparator.comparingInt(String::length));
        for (String rel : sorted) {
            Files.createDirectories(tilesDir.resolve(rel));
        }
    }

}
