/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.encoder.tiles3d;

import org.citydb.vis.util.JsonHelper;
import org.citydb.vis.model.AttrField;
import org.citydb.vis.model.tiles3d.TileBoundingVolume;
import org.citydb.vis.model.tiles3d.TileNode;
import org.citydb.vis.model.tiles3d.TilesetDescriptor;
import org.citydb.vis.scene.SceneNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Writes multi-level 3D Tiles 1.1 tileset JSON files via
 * {@link TilesetDescriptor} POJOs.
 * <p>
 * The writer traverses a combined tree (aggregation layer from
 * {@link CellAggregator} wrapping per-cell quadtrees). Whenever a subtree
 * would exceed {@link #MAX_NODES_PER_SUBTILESET}, the child is externalized
 * at that natural tree boundary into its own subtree file, ensuring each
 * file is spatially coherent. Split subtree files live at a path derived
 * from the scene node's position in the tree, mirroring the {@code tiles/}
 * layout.
 */
public class TilesetSerializer {

    /**
     * Maximum nodes per sub-tileset file. When a child's subtree would
     * push the current file over this limit, it is written as a separate
     * external tileset at the natural tree boundary.
     */
    static final int MAX_NODES_PER_SUBTILESET = 64;

    /**
     * Traversal-invariant state threaded through the recursive subtree
     * writers. A single {@code Ctx} is reused across every subtree file
     * produced from one aggregation root.
     */
    private record Ctx(Set<Integer> meshNodeIndices,
                       Map<Integer, int[]> tilePaths,
                       Map<Integer, Integer> subtreeSizes,
                       Path subtreesDir,
                       AtomicInteger subtreeFileCount) {
    }

    // ---- Root tileset ---------------------------------------------------

    /**
     * Write the top-level {@code tileset.json}. Carries the metadata schema
     * and the ENU-to-ECEF transform, and contains a single external ref to
     * the aggregation root's subtree file — regardless of cell count, so the
     * root stays small for city-scale datasets.
     */
    public void writeRootTileset(Path outputDir, SceneNode globalRoot,
                                 SceneNode aggRoot,
                                 double[] extent, List<AttrField> attrFields,
                                 double[] transform,
                                 Map<Integer, int[]> tilePaths) throws IOException {
        // Root's geometricError must be > 0 so Cesium's SSE check triggers
        // refinement into the subtree child. Using globalRoot's R/8 covers
        // the full dataset and stays > 0 as long as any geometry exists,
        // which also works for small/shallow datasets where leaf-derived
        // errors would be 0 (Cesium would then render the root's null
        // content instead of loading children).
        double rootGeo = TileNode.computeGeometricError(globalRoot);
        int[] aggPath = tilePaths.get(aggRoot.getIndex());
        double aggGeo = TileNode.computeGeometricError(aggRoot);
        String aggUri = "subtrees/" + TilePaths.subtreeFile(aggPath);

        TilesetDescriptor descriptor = TilesetDescriptor.ofRoot(
                rootGeo, extent, transform, attrFields,
                TileBoundingVolume.fromBoundingVolume(aggRoot.getBoundingVolume()),
                aggGeo, aggUri);

        JsonHelper.writePojo(outputDir.resolve("tileset.json"), descriptor);
    }

    // ---- Tree-based sub-tilesets ----------------------------------------

    public void writeSubTileset(Path subtreesDir, SceneNode subtreeRoot,
                                Set<Integer> meshNodeIndices,
                                Map<Integer, int[]> tilePaths,
                                AtomicInteger subtreeFileCount) throws IOException {
        Map<Integer, Integer> subtreeSizes = new HashMap<>();
        computeSubtreeSize(subtreeRoot, subtreeSizes);

        int[] rootPath = tilePaths.get(subtreeRoot.getIndex());
        Ctx ctx = new Ctx(meshNodeIndices, tilePaths, subtreeSizes,
                subtreesDir, subtreeFileCount);
        writeSubtreeFile(ctx, subtreeRoot, rootPath);
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

    private static void writeSubtreeFile(Ctx ctx, SceneNode root,
                                         int[] rootPath) throws IOException {
        // The subtree file lives at subtreesDir/<rootPath>.json; tile content
        // URIs must navigate up rootPath.length levels to reach the output
        // root, then back down into tiles/. Hoist the prefix here so
        // buildTileNode doesn't rebuild the same string for every mesh node.
        String tilePrefix = "../".repeat(rootPath.length) + "tiles/";
        double rootGeo = TileNode.computeGeometricError(root);
        TileNode rootTile = buildTileNode(ctx, root, rootPath, tilePrefix,
                new AtomicInteger(), rootGeo);

        Path out = ctx.subtreesDir().resolve(TilePaths.subtreeFile(rootPath));
        Files.createDirectories(out.getParent());
        JsonHelper.writePojo(out, TilesetDescriptor.ofSubtileset(rootGeo, rootTile));
        ctx.subtreeFileCount().incrementAndGet();
    }

    private static TileNode buildTileNode(Ctx ctx, SceneNode node,
                                          int[] currentSubtreeRootPath,
                                          String tilePrefix,
                                          AtomicInteger nodeCount,
                                          double overrideGeo) throws IOException {
        nodeCount.incrementAndGet();
        String contentUri = ctx.meshNodeIndices().contains(node.getIndex())
                ? tilePrefix + TilePaths.tileFile(ctx.tilePaths().get(node.getIndex()))
                : null;
        TileNode tile = TileNode.of(node, overrideGeo, contentUri);

        for (SceneNode child : node.getChildren()) {
            int childSize = ctx.subtreeSizes().getOrDefault(child.getIndex(), 1);

            if (nodeCount.get() + childSize > MAX_NODES_PER_SUBTILESET) {
                int[] childPath = ctx.tilePaths().get(child.getIndex());
                String uri = TilePaths.relativeSubtreeUri(currentSubtreeRootPath, childPath);
                tile.addChild(TileNode.ofExternalRef(child, uri));
                writeSubtreeFile(ctx, child, childPath);
            } else {
                tile.addChild(buildTileNode(ctx, child, currentSubtreeRootPath,
                        tilePrefix, nodeCount, -1));
            }
        }

        return tile;
    }

}
