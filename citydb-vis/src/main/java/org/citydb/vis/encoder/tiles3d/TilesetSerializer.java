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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Writes multi-level 3D Tiles 1.1 tileset JSON files via
 * {@link TilesetDescriptor} POJOs.
 * <p>
 * Structural per-level split: every scene node that has children is
 * externalized as its own subtree JSON file; only childless leaves (cell
 * mesh nodes / mixed-split textured+untextured pair) stay inline with
 * their GLB content on the parent. Each resulting JSON therefore has a
 * uniform shape — a single aggregation root plus its direct children,
 * which are either external subtree references or inline mesh leaves.
 * Split subtree files live at a path derived from the scene node's
 * position in the aggregation tree, mirroring the {@code tiles/} layout.
 */
public class TilesetSerializer {

    /**
     * Traversal-invariant state threaded through the recursive subtree
     * writers. A single {@code Ctx} is reused across every subtree file
     * produced from one aggregation root.
     */
    private record Ctx(Set<Integer> meshNodeIndices,
                       Map<Integer, int[]> tilePaths,
                       Path subtreesDir,
                       AtomicInteger subtreeFileCount,
                       double geRatio) {
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
                                 Map<Integer, int[]> tilePaths,
                                 double geRatio) throws IOException {
        // Root's geometricError must be > 0 so Cesium's SSE check triggers
        // refinement into the subtree child. Using globalRoot's R × geRatio
        // covers the full dataset and stays > 0 as long as any geometry
        // exists, which also works for small/shallow datasets where
        // leaf-derived errors would be 0 (Cesium would then render the
        // root's null content instead of loading children).
        double rootGeo = TileNode.computeGeometricError(globalRoot, geRatio);
        int[] aggPath = tilePaths.get(aggRoot.getIndex());
        double aggGeo = TileNode.computeGeometricError(aggRoot, geRatio);
        String aggUri = "subtrees/" + TilePaths.subtreeFile(aggPath);

        TilesetDescriptor descriptor = TilesetDescriptor.ofRoot(
                rootGeo, extent, transform, attrFields,
                TileBoundingVolume.fromBoundingVolume(aggRoot.getBoundingVolume()),
                aggGeo, aggUri);

        JsonHelper.writePojo(outputDir.resolve("tileset.json"), descriptor);
    }

    // ---- Per-level sub-tilesets -----------------------------------------

    public void writeSubTileset(Path subtreesDir, SceneNode subtreeRoot,
                                Set<Integer> meshNodeIndices,
                                Map<Integer, int[]> tilePaths,
                                AtomicInteger subtreeFileCount,
                                double geRatio) throws IOException {
        int[] rootPath = tilePaths.get(subtreeRoot.getIndex());
        Ctx ctx = new Ctx(meshNodeIndices, tilePaths,
                subtreesDir, subtreeFileCount, geRatio);
        writeSubtreeFile(ctx, subtreeRoot, rootPath);
    }

    private static void writeSubtreeFile(Ctx ctx, SceneNode root,
                                         int[] rootPath) throws IOException {
        // The subtree file lives at subtreesDir/<rootPath>.json; tile content
        // URIs must navigate up rootPath.length levels to reach the output
        // root, then back down into tiles/. Hoist the prefix here so
        // buildTileNode doesn't rebuild the same string for every mesh leaf.
        String tilePrefix = "../".repeat(rootPath.length) + "tiles/";
        double rootGeo = TileNode.computeGeometricError(root, ctx.geRatio());
        TileNode rootTile = buildTileNode(ctx, root, rootPath, tilePrefix, rootGeo);

        Path out = ctx.subtreesDir().resolve(TilePaths.subtreeFile(rootPath));
        Files.createDirectories(out.getParent());
        JsonHelper.writePojo(out, TilesetDescriptor.ofSubtileset(rootGeo, rootTile));
        ctx.subtreeFileCount().incrementAndGet();
    }

    /**
     * Build a single tile: {@code node} becomes the inline root, its
     * children become either external refs (if they have children of their
     * own — always the case for aggregation nodes and mixed cell roots)
     * or inline mesh leaves pointing at their GLB. The depth of any one
     * JSON is therefore exactly two: root + direct children.
     */
    private static TileNode buildTileNode(Ctx ctx, SceneNode node,
                                          int[] currentSubtreeRootPath,
                                          String tilePrefix,
                                          double geometricError) throws IOException {
        String contentUri = ctx.meshNodeIndices().contains(node.getIndex())
                ? tilePrefix + TilePaths.tileFile(ctx.tilePaths().get(node.getIndex()))
                : null;
        TileNode tile = TileNode.of(node, geometricError, contentUri);

        for (SceneNode child : node.getChildren()) {
            double childGeo = TileNode.computeGeometricError(child, ctx.geRatio());

            if (child.getChildren().isEmpty()) {
                // Leaf: inline with its GLB content (or null if not a mesh node).
                tile.addChild(buildTileNode(ctx, child, currentSubtreeRootPath,
                        tilePrefix, childGeo));
            } else {
                // Aggregation / intermediate: externalize to its own subtree file.
                int[] childPath = ctx.tilePaths().get(child.getIndex());
                String uri = TilePaths.relativeSubtreeUri(currentSubtreeRootPath, childPath);
                tile.addChild(TileNode.ofExternalRef(child, childGeo, uri));
                writeSubtreeFile(ctx, child, childPath);
            }
        }

        return tile;
    }

}
