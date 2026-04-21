/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences <https://www.hft-stuttgart.de>
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
 * coherent. Split subtree files live at a path derived from the scene
 * node's position in the tree, mirroring the {@code tiles/} layout.
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
     * writers. A single {@code Ctx} is reused across all subtree files
     * produced from one cell root.
     */
    private record Ctx(Set<Integer> meshNodeIndices,
                       Map<Integer, int[]> tilePaths,
                       Map<Integer, Integer> subtreeSizes,
                       Path subtreesDir,
                       AtomicInteger subtreeFileCount) {
    }

    // ---- Root tileset ---------------------------------------------------

    public void writeRootTileset(Path outputDir, SceneNode globalRoot,
                                 double[] extent, List<AttrField> attrFields,
                                 double[] transform,
                                 Map<Integer, int[]> tilePaths) throws IOException {
        // Root's geometricError must be > 0 so Cesium's SSE check triggers
        // refinement into the cellRef children. Taking max of cellRoot errors
        // breaks for small/shallow datasets (single cell whose root is a leaf
        // mesh), where every cellRoot error is 0 — the root then never refines
        // and renders its own null content (blank screen). Use the globalRoot's
        // own R/8 instead: its radius covers the full dataset, and it has
        // cellRoots as children, so computeGeometricError() returns > 0 as
        // long as any geometry exists.
        List<SceneNode> cellRoots = globalRoot.getChildren();
        List<CellReference> cellRefs = new ArrayList<>(cellRoots.size());
        for (SceneNode cellRoot : cellRoots) {
            double geo = TileNode.computeGeometricError(cellRoot);
            BoundingVolume bv = cellRoot.getBoundingVolume();
            int[] rootPath = tilePaths.get(cellRoot.getIndex());
            cellRefs.add(new CellReference(
                    TileBoundingVolume.fromBoundingVolume(bv),
                    geo,
                    "subtrees/" + TilePaths.subtreeFile(rootPath)));
        }

        double rootGeo = TileNode.computeGeometricError(globalRoot);
        TilesetDescriptor descriptor = TilesetDescriptor.ofRoot(
                rootGeo, extent, transform, cellRefs, attrFields);

        JsonHelper.writePojo(outputDir.resolve("tileset.json"), descriptor);
    }

    // ---- Tree-based sub-tilesets ----------------------------------------

    public void writeSubTileset(Path subtreesDir, SceneNode cellRoot,
                                Set<Integer> meshNodeIndices,
                                Map<Integer, int[]> tilePaths,
                                AtomicInteger subtreeFileCount) throws IOException {
        Map<Integer, Integer> subtreeSizes = new HashMap<>();
        computeSubtreeSize(cellRoot, subtreeSizes);

        int[] rootPath = tilePaths.get(cellRoot.getIndex());
        Ctx ctx = new Ctx(meshNodeIndices, tilePaths, subtreeSizes,
                subtreesDir, subtreeFileCount);
        writeSubtreeFile(ctx, cellRoot, rootPath);
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
