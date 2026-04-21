/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences (HFT Stuttgart) <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.pipeline;

import org.citydb.vis.scene.BoundingVolume;
import org.citydb.vis.scene.SceneNode;
import org.citydb.vis.store.NodeEntry;
import org.citydb.vis.store.NodeEntryStore;
import org.citydb.vis.store.SpatialEntry;
import org.citydb.vis.util.BoundingBoxUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds scene node hierarchies using a two-level spatial indexing strategy:
 * <ol>
 *   <li><b>Grid partitioning</b> (in {@link org.citydb.vis.pipeline.stages.PartitioningStage}):
 *       features are classified into spatial grid cells via
 *       {@link org.citydb.vis.store.PartitionedEntryStore}.</li>
 *   <li><b>Per-cell quadtree</b> (here): each grid cell independently builds
 *       a quadtree from its {@link SpatialEntry} metadata. No triangle meshes
 *       are loaded during tree construction.</li>
 * </ol>
 * After each cell tree is built,
 * {@link org.citydb.vis.pipeline.stages.TreeBuildingStage} remaps its node
 * indices into the global tree and flushes the compact {@link NodeEntry}
 * lists to disk via {@link org.citydb.vis.store.NodeEntryStore}.
 * <p>
 * This builder is format-agnostic — it produces a spatial hierarchy of
 * {@link SceneNode} objects without applying format-specific properties
 * such as LOD thresholds or geometric errors. The caller is responsible
 * for setting those after the tree is built.
 */
public class NodeBuilder {
    /**
     * Result of building a quadtree for a single spatial grid cell.
     * <ul>
     *   <li>{@code nodes} — scene nodes in local (cell-scoped) index order;
     *       index 0 is the cell root. Indices are remapped to global scope
     *       by the caller before flushing.</li>
     *   <li>{@code nodeEntryMap} — for each leaf node (keyed by its local
     *       index), the list of features assigned to it. Only leaves carry
     *       entries; interior nodes are absent from the map. Each
     *       {@link NodeEntry} holds just the feature id + store handles;
     *       the {@link SpatialEntry} spatial fields (centerX/Y/bbox) are
     *       consumed during tree construction and dropped.</li>
     * </ul>
     * The map is a separate field (not embedded in {@link SceneNode}) so
     * entries can be flushed to {@link NodeEntryStore} and released from
     * heap as soon as each cell is processed.
     */
    public record CellTree(List<SceneNode> nodes, Map<Integer, List<NodeEntry>> nodeEntryMap) {
    }

    /**
     * Build a local quadtree for entries within a single grid cell.
     * <p>
     * Thread-safe — no shared mutable state. Can be called concurrently
     * for different cells.
     */
    public static CellTree buildCellTree(List<SpatialEntry> entries, double[] extent,
                                  int maxFeaturesPerNode, int maxTreeDepth) {
        Map<Integer, List<NodeEntry>> nodeEntryMap = new HashMap<>();
        List<SceneNode> nodes = new ArrayList<>();
        int[] counter = {0};

        SceneNode root = new SceneNode(counter[0]++, 0);
        nodes.add(root);

        subdivide(root, entries, extent,
                maxFeaturesPerNode, maxTreeDepth,
                nodes, counter, nodeEntryMap);

        updateBoundingVolumes(root);

        return new CellTree(nodes, nodeEntryMap);
    }

    /**
     * Set the bounding volume on the global root node after all cell trees
     * have been attached as children.
     */
    public static void finalizeGlobalRoot(SceneNode globalRoot) {
        globalRoot.updateBoundingVolume();
    }

    /**
     * Compute the axis-aligned bounding box of a list of spatial entries.
     */
    public static double[] computeExtent(List<SpatialEntry> entries) {
        double[] acc = BoundingBoxUtils.emptyAabb();
        for (SpatialEntry e : entries) {
            BoundingBoxUtils.expandToBox(acc, e.bbox());
        }
        return acc;
    }

    // ---- Quadtree subdivision (operates on SpatialEntry) ----

    /**
     * Recursively partition entries into a quadtree.
     * <p>
     * At leaf nodes, bounding volumes are computed from the entries' bounding
     * boxes and entries are compacted to {@link NodeEntry} (dropping spatial
     * fields that are no longer needed). This allows the caller's
     * {@link SpatialEntry} list to be GC'd as soon as the tree is built.
     */
    private static void subdivide(SceneNode node, List<SpatialEntry> entries,
                                  double[] extent, int maxPerNode, int maxDepth,
                                  List<SceneNode> allNodes, int[] nodeCounter,
                                  Map<Integer, List<NodeEntry>> nodeEntryMap) {
        if (entries.size() <= maxPerNode || node.getLevel() >= maxDepth) {
            node.setFeatureCount(entries.size());

            // Compute bounding volume + compact to NodeEntry in one pass
            double[] acc = BoundingBoxUtils.emptyAabb();
            List<NodeEntry> compact = new ArrayList<>(entries.size());
            for (SpatialEntry e : entries) {
                BoundingBoxUtils.expandToBox(acc, e.bbox());
                compact.add(new NodeEntry(e.id(), e.meshHandle(), e.attrOffset()));
            }
            node.setBoundingVolume(BoundingVolume.ofBoundingBox(
                    acc[0], acc[1], acc[2], acc[3], acc[4], acc[5]));
            nodeEntryMap.put(node.getIndex(), compact);
            return;
        }

        double midX = (extent[0] + extent[3]) / 2;
        double midY = (extent[1] + extent[4]) / 2;

        double[][] childExtents = {
                {extent[0], extent[1], extent[2], midX, midY, extent[5]},     // SW
                {midX, extent[1], extent[2], extent[3], midY, extent[5]},     // SE
                {extent[0], midY, extent[2], midX, extent[4], extent[5]},     // NW
                {midX, midY, extent[2], extent[3], extent[4], extent[5]}      // NE
        };

        // Single-pass partitioning: classify each entry into one of 4 quadrants
        @SuppressWarnings("unchecked")
        List<SpatialEntry>[] buckets = new List[4];
        for (int i = 0; i < 4; i++) buckets[i] = new ArrayList<>();

        for (SpatialEntry entry : entries) {
            int qx = entry.centerX() > midX ? 1 : 0;
            int qy = entry.centerY() > midY ? 1 : 0;
            buckets[qx + qy * 2].add(entry);
        }

        for (int q = 0; q < 4; q++) {
            if (!buckets[q].isEmpty()) {
                SceneNode child = new SceneNode(nodeCounter[0]++, node.getLevel() + 1);
                allNodes.add(child);
                node.addChild(child);
                subdivide(child, buckets[q], childExtents[q], maxPerNode, maxDepth,
                        allNodes, nodeCounter, nodeEntryMap);
            }
        }
    }

    private static void updateBoundingVolumes(SceneNode node) {
        for (SceneNode child : node.getChildren()) {
            updateBoundingVolumes(child);
        }
        node.updateBoundingVolume();
    }
}
