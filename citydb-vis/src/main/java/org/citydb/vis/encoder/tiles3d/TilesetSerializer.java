/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.vis.encoder.tiles3d;

import org.citydb.vis.util.JsonHelper;
import org.citydb.vis.model.AttrField;
import org.citydb.vis.model.tiles3d.CellReference;
import org.citydb.vis.model.tiles3d.TileBoundingVolume;
import org.citydb.vis.model.tiles3d.TileNode;
import org.citydb.vis.model.tiles3d.TilesetDescriptor;
import org.citydb.vis.scene.BoundingVolume;
import org.citydb.vis.scene.SceneNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Writes multi-level 3D Tiles 1.1 tileset JSON files via
 * {@link TilesetDescriptor} POJOs.
 * <p>
 * When a cell's quadtree exceeds {@link #MAX_NODES_PER_SUBTILESET}
 * nodes, deeper subtrees are split into separate external tileset
 * files at natural tree boundaries, ensuring each file is spatially
 * coherent.
 */
public class TilesetSerializer {

    /**
     * Maximum nodes per sub-tileset file. When a child's subtree would
     * push the current file over this limit, it is written as a separate
     * external tileset at the natural tree boundary.
     */
    static final int MAX_NODES_PER_SUBTILESET = 64;

    // ---- Root tileset ---------------------------------------------------

    public void writeRootTileset(Path outputDir, SceneNode globalRoot,
                                 Set<Integer> meshNodeIndices,
                                 double[] extent, List<AttrField> attrFields,
                                 double[] transform) throws IOException {
        // With ADD refinement, all tiles use consistent R/16 geometric
        // errors — no need for "always refine" overrides. ADD never causes
        // flickering on content-less nodes, and consistent errors prevent
        // shallow leaf nodes from rendering prematurely at far distances.
        double maxGeo = 0;
        for (SceneNode cellRoot : globalRoot.getChildren()) {
            double geo = TileNode.computeGeometricError(cellRoot);
            maxGeo = Math.max(maxGeo, geo);
        }

        List<CellReference> cellRefs = new ArrayList<>();
        List<SceneNode> cellRoots = globalRoot.getChildren();
        for (int i = 0; i < cellRoots.size(); i++) {
            BoundingVolume bv = cellRoots.get(i).getBoundingVolume();
            cellRefs.add(new CellReference(
                    TileBoundingVolume.fromMbs(bv),
                    TileNode.computeGeometricError(cellRoots.get(i)),
                    "subtrees/" + i + ".json"));
        }

        TilesetDescriptor descriptor = TilesetDescriptor.ofRoot(
                maxGeo, extent, transform, cellRefs, attrFields);

        JsonHelper.writePojo(outputDir.resolve("tileset.json"), descriptor);
    }

    // ---- Tree-based sub-tilesets ----------------------------------------

    public void writeSubTileset(Path subtreeFile, SceneNode cellRoot,
                                Set<Integer> meshNodeIndices,
                                AtomicInteger subtreeCounter) throws IOException {
        Map<Integer, Integer> subtreeSizes = new HashMap<>();
        computeSubtreeSize(cellRoot, subtreeSizes);

        Path subtreesDir = subtreeFile.getParent();
        double rootGeo = TileNode.computeGeometricError(cellRoot);

        int[] nodeCount = {0};
        TileNode rootTile = buildTileNode(cellRoot, meshNodeIndices,
                subtreeSizes, subtreesDir, subtreeCounter, nodeCount,
                rootGeo);

        TilesetDescriptor descriptor = TilesetDescriptor.ofSubtileset(
                rootGeo, rootTile);

        Files.createDirectories(subtreesDir);
        JsonHelper.writePojo(subtreeFile, descriptor);
    }

    // ---- Tree traversal with spatial splitting --------------------------

    private static int computeSubtreeSize(SceneNode node,
                                          Map<Integer, Integer> sizes) {
        int size = 1;
        for (SceneNode child : node.getChildren()) {
            size += computeSubtreeSize(child, sizes);
        }
        sizes.put(node.getIndex(), size);
        return size;
    }

    private static TileNode buildTileNode(SceneNode node, Set<Integer> meshNodeIndices,
                                          Map<Integer, Integer> subtreeSizes,
                                          Path subtreesDir,
                                          AtomicInteger subtreeCounter,
                                          int[] nodeCount,
                                          double overrideGeo) {
        nodeCount[0]++;
        TileNode tile = TileNode.of(node, meshNodeIndices, overrideGeo);

        for (SceneNode child : node.getChildren()) {
            int childSize = subtreeSizes.getOrDefault(child.getIndex(), 1);

            if (nodeCount[0] + childSize > MAX_NODES_PER_SUBTILESET) {
                // Split: write child's subtree as a separate external tileset
                int splitIdx = subtreeCounter.getAndIncrement();
                tile.addChild(TileNode.ofExternalRef(child, splitIdx + ".json"));
                writeExternalSubtileset(child, meshNodeIndices, subtreeSizes,
                        subtreesDir, subtreeCounter, splitIdx);
            } else {
                tile.addChild(buildTileNode(child, meshNodeIndices,
                        subtreeSizes, subtreesDir, subtreeCounter,
                        nodeCount, -1));
            }
        }

        return tile;
    }

    private static void writeExternalSubtileset(SceneNode root,
                                                Set<Integer> meshNodeIndices,
                                                Map<Integer, Integer> subtreeSizes,
                                                Path subtreesDir,
                                                AtomicInteger subtreeCounter,
                                                int fileIndex) {
        double rootGeo = TileNode.computeGeometricError(root);

        int[] nodeCount = {0};
        TileNode rootTile = buildTileNode(root, meshNodeIndices,
                subtreeSizes, subtreesDir, subtreeCounter, nodeCount,
                rootGeo);

        TilesetDescriptor descriptor = TilesetDescriptor.ofSubtileset(
                rootGeo, rootTile);

        Path file = subtreesDir.resolve(fileIndex + ".json");
        try {
            JsonHelper.writePojo(file, descriptor);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write external subtileset: " + file, e);
        }
    }

}
