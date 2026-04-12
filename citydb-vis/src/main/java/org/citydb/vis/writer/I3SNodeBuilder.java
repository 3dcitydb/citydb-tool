/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.vis.writer;

import org.citydb.vis.I3SFormatOptions;
import org.citydb.vis.scene.BoundingVolume;
import org.citydb.vis.scene.I3SNode;
import org.citydb.vis.store.NodeEntry;
import org.citydb.vis.store.SpatialEntry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds I3S node hierarchies using a two-level spatial indexing strategy:
 * <ol>
 *   <li><b>Grid partitioning</b> (in the writer): features are classified into
 *       spatial grid cells via {@link org.citydb.vis.store.PartitionedEntryStore}.</li>
 *   <li><b>Per-cell quadtree</b> (here): each grid cell independently builds
 *       a quadtree from its {@link SpatialEntry} metadata. No triangle meshes
 *       are loaded during tree construction.</li>
 * </ol>
 * After each cell tree is built, the writer remaps its node indices into the
 * global tree and flushes the compact {@link NodeEntry} lists to disk via
 * {@link org.citydb.vis.store.NodeEntryStore}.
 */
class I3SNodeBuilder {
    /**
     * LOD threshold used by I3S runtimes (CesiumJS) as a screen-space area
     * (px²) above which a node should refine to its children.
     * <p>
     * A <b>larger</b> value means the node stays visible longer before being
     * replaced by its children.
     * <ul>
     *   <li>Leaf nodes hold the actual geometry and must remain visible across
     *       a wide range of screen areas — they use the larger value.</li>
     *   <li>Internal nodes have no geometry (no LOD pyramid yet) and must
     *       refine to children as soon as meaningful screen area is reached —
     *       they use the smaller value.</li>
     * </ul>
     * These values will need to be revisited when LOD geometry is generated
     * for intermediate nodes.
     */
    private static final double LEAF_NODE_LOD_THRESHOLD = 131_072;
    private static final double INTERNAL_NODE_LOD_THRESHOLD = 65_536;

    /**
     * Result of building a quadtree for a single spatial grid cell.
     * The {@code nodeEntryMap} holds compact {@link NodeEntry} lists — spatial
     * fields (centerX, centerY, bbox) are consumed during tree construction
     * and not retained.
     */
    record CellTree(List<I3SNode> nodes, Map<Integer, List<NodeEntry>> nodeEntryMap) {
    }

    /**
     * Build a local quadtree for entries within a single grid cell.
     * <p>
     * Thread-safe — no shared mutable state. Can be called concurrently
     * for different cells.
     */
    static CellTree buildCellTree(List<SpatialEntry> entries, double[] extent,
                                  I3SFormatOptions formatOptions) {
        Map<Integer, List<NodeEntry>> nodeEntryMap = new HashMap<>();
        List<I3SNode> nodes = new ArrayList<>();
        int[] counter = {0};

        I3SNode root = new I3SNode(counter[0]++, 0);
        nodes.add(root);

        subdivide(root, entries, extent,
                formatOptions.getMaxFeaturesPerNode(),
                formatOptions.getMaxTreeDepth(),
                nodes, counter, nodeEntryMap);

        updateBoundingVolumes(root);
        setLodThresholds(root);

        return new CellTree(nodes, nodeEntryMap);
    }

    /**
     * Set the bounding volume and LOD threshold on the global root node
     * after all cell trees have been attached as children.
     */
    static void finalizeGlobalRoot(I3SNode globalRoot) {
        globalRoot.updateBoundingVolume();
        globalRoot.setLodThreshold(INTERNAL_NODE_LOD_THRESHOLD);
    }

    /**
     * Compute the axis-aligned bounding box of a list of spatial entries.
     */
    static double[] computeExtent(List<SpatialEntry> entries) {
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, minZ = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;

        for (SpatialEntry e : entries) {
            double[] bb = e.bbox();
            if (bb[0] < minX) minX = bb[0];
            if (bb[1] < minY) minY = bb[1];
            if (bb[2] < minZ) minZ = bb[2];
            if (bb[3] > maxX) maxX = bb[3];
            if (bb[4] > maxY) maxY = bb[4];
            if (bb[5] > maxZ) maxZ = bb[5];
        }

        return new double[]{minX, minY, minZ, maxX, maxY, maxZ};
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
    private static void subdivide(I3SNode node, List<SpatialEntry> entries,
                                  double[] extent, int maxPerNode, int maxDepth,
                                  List<I3SNode> allNodes, int[] nodeCounter,
                                  Map<Integer, List<NodeEntry>> nodeEntryMap) {
        if (entries.size() <= maxPerNode || node.getLevel() >= maxDepth) {
            node.setFeatureCount(entries.size());

            // Compute bounding volume + compact to NodeEntry in one pass
            double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, minZ = Double.MAX_VALUE;
            double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
            List<NodeEntry> compact = new ArrayList<>(entries.size());
            for (SpatialEntry e : entries) {
                double[] bb = e.bbox();
                if (bb[0] < minX) minX = bb[0];
                if (bb[1] < minY) minY = bb[1];
                if (bb[2] < minZ) minZ = bb[2];
                if (bb[3] > maxX) maxX = bb[3];
                if (bb[4] > maxY) maxY = bb[4];
                if (bb[5] > maxZ) maxZ = bb[5];
                compact.add(new NodeEntry(e.id(), e.meshHandle(), e.attrOffset()));
            }
            node.setMbs(BoundingVolume.ofBoundingBox(minX, minY, minZ, maxX, maxY, maxZ));
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
                I3SNode child = new I3SNode(nodeCounter[0]++, node.getLevel() + 1);
                allNodes.add(child);
                node.addChild(child);
                subdivide(child, buckets[q], childExtents[q], maxPerNode, maxDepth,
                        allNodes, nodeCounter, nodeEntryMap);
            }
        }
    }

    private static void updateBoundingVolumes(I3SNode node) {
        for (I3SNode child : node.getChildren()) {
            updateBoundingVolumes(child);
        }
        node.updateBoundingVolume();
    }

    private static void setLodThresholds(I3SNode node) {
        boolean isLeaf = node.getChildren().isEmpty();
        node.setLodThreshold(isLeaf ? LEAF_NODE_LOD_THRESHOLD : INTERNAL_NODE_LOD_THRESHOLD);

        for (I3SNode child : node.getChildren()) {
            setLodThresholds(child);
        }
    }
}
