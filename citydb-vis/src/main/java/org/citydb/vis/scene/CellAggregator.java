/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences (HFT Stuttgart) <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.scene;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Groups per-cell leaves under a spatial aggregation tree.
 * <p>
 * Without aggregation, every populated grid cell would sit as a direct
 * child of {@code globalRoot} — a flat fanout that scales with
 * {@code gridDim^2} (thousands of entries for city-scale datasets).
 * Downstream consumers that rely on hierarchical LOD (3D Tiles tileset
 * refinement, I3S node-page refinement) would have no intermediate layers
 * between the root and the cells and would therefore ask the runtime to
 * load every cell as soon as the root refines.
 * <p>
 * The aggregation tree inserts synthetic 2×2 parent nodes grouping cells
 * by their grid coordinates, giving each level a well-defined spatial
 * extent and geometric error. For 3D Tiles this, combined with the
 * subtree-size split, keeps every {@code tileset.json}/{@code subtree.json}
 * bounded regardless of cell count. For I3S it provides the progressive
 * refinement cascade in the node pages.
 * <p>
 * Synthetic aggregation {@link SceneNode}s get indices above the scene
 * tree's existing node range. They carry bounding volumes merged from
 * their children but no mesh, feature entries, or output content.
 */
public final class CellAggregator {

    /**
     * Maximum number of cell roots (or sub-aggregations) collapsed directly
     * under a single aggregation node. Smaller values produce a deeper
     * aggregation tree; 4 matches the 2×2 grid-coord split and minimizes
     * extra depth for typical cell layouts.
     */
    static final int MAX_CHILDREN_PER_AGG_NODE = 4;

    private CellAggregator() {
    }

    /**
     * Build an aggregation tree wrapping the given cell roots. The returned
     * node has all cell roots (and any descendants attached by earlier
     * stages such as {@link
     * org.citydb.vis.pipeline.stages.MixedNodeSplitStage}) as descendants;
     * bounding volumes propagate bottom-up.
     *
     * @param cellRoots  direct children of {@code globalRoot}
     * @param gridCoords grid {@code [gy, gx]} for each cell root
     * @param indexer    supplies fresh indices for synthetic aggregation nodes
     */
    public static SceneNode build(List<SceneNode> cellRoots,
                                  Map<Integer, int[]> gridCoords,
                                  AtomicInteger indexer) {
        // Even for a single cell, wrap in an aggregation node so the output
        // path scheme is uniform (aggregation root at [0], descendants below).
        List<Cell> cells = new ArrayList<>(cellRoots.size());
        int minGy = Integer.MAX_VALUE, minGx = Integer.MAX_VALUE;
        int maxGy = Integer.MIN_VALUE, maxGx = Integer.MIN_VALUE;
        for (SceneNode cellRoot : cellRoots) {
            int[] gc = gridCoords.get(cellRoot.getIndex());
            cells.add(new Cell(cellRoot, gc[0], gc[1]));
            minGy = Math.min(minGy, gc[0]);
            maxGy = Math.max(maxGy, gc[0]);
            minGx = Math.min(minGx, gc[1]);
            maxGx = Math.max(maxGx, gc[1]);
        }

        return buildRecursive(cells, minGy, maxGy, minGx, maxGx, indexer);
    }

    private static SceneNode buildRecursive(List<Cell> cells,
                                            int minGy, int maxGy,
                                            int minGx, int maxGx,
                                            AtomicInteger indexer) {
        SceneNode agg = new SceneNode(indexer.getAndIncrement(), 0);

        // Leaf aggregation: fanout small enough, or all cells collapsed to
        // one grid coord (can't split further on either axis).
        boolean canSplit = maxGy > minGy || maxGx > minGx;
        if (cells.size() <= MAX_CHILDREN_PER_AGG_NODE || !canSplit) {
            for (Cell c : cells) {
                agg.addChild(c.node);
            }
            agg.updateBoundingVolume();
            return agg;
        }

        // Split 4 ways by grid-coord midpoint, mid inclusive on the low side.
        // When one axis has collapsed (min == max on that axis) the other
        // axis still drives the split — the collapsed axis's bit is always 0,
        // so only the SW/NW (or SW/SE) buckets populate and recursion
        // continues until both axes collapse and the canSplit guard above
        // terminates.
        int midGy = (minGy + maxGy) / 2;
        int midGx = (minGx + maxGx) / 2;

        @SuppressWarnings("unchecked")
        List<Cell>[] buckets = new List[4];
        for (int i = 0; i < 4; i++) buckets[i] = new ArrayList<>();
        for (Cell c : cells) {
            int qx = c.gx > midGx ? 1 : 0;
            int qy = c.gy > midGy ? 1 : 0;
            buckets[qx + qy * 2].add(c);
        }

        int[][] quadBbox = {
                {minGy, midGy, minGx, midGx},           // SW (gy<=mid, gx<=mid)
                {minGy, midGy, midGx + 1, maxGx},       // SE
                {midGy + 1, maxGy, minGx, midGx},       // NW
                {midGy + 1, maxGy, midGx + 1, maxGx}    // NE
        };

        for (int q = 0; q < 4; q++) {
            List<Cell> bucket = buckets[q];
            if (bucket.isEmpty()) continue;
            int[] bb = quadBbox[q];
            agg.addChild(buildRecursive(bucket, bb[0], bb[1], bb[2], bb[3], indexer));
        }

        agg.updateBoundingVolume();
        return agg;
    }

    private record Cell(SceneNode node, int gy, int gx) {
    }
}
