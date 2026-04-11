/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.vis.writer;

import org.citydb.vis.I3SFormatOptions;
import org.citydb.vis.scene.BoundingVolume;
import org.citydb.vis.scene.I3SNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Builds I3S node hierarchies using a two-level spatial indexing strategy:
 * <ol>
 *   <li><b>Grid partitioning</b> (in the writer): features are classified into
 *       spatial grid cells during the write phase.</li>
 *   <li><b>Per-cell quadtree</b> (here): each grid cell independently builds
 *       a quadtree from its {@link SpatialEntry} metadata. No triangle meshes
 *       are loaded during tree construction.</li>
 * </ol>
 * After all cell trees are built (in parallel), they are merged into a single
 * global tree with sequential node indices via {@link #mergeIntoGlobalTree}.
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
     */
    record CellTree(List<I3SNode> nodes, Map<Integer, List<SpatialEntry>> nodeEntryMap) {
    }

    /**
     * Result of merging all cell trees into a global tree.
     */
    record MergedTree(List<I3SNode> allNodes,
                      Map<Integer, List<SpatialEntry>> nodeEntryMap,
                      Set<Integer> meshNodeIndices) {
    }

    /**
     * Build a local quadtree for entries within a single grid cell.
     * <p>
     * Thread-safe — no shared mutable state. Can be called concurrently
     * for different cells.
     */
    static CellTree buildCellTree(List<SpatialEntry> entries, double[] extent,
                                  I3SFormatOptions formatOptions) {
        Map<Integer, List<SpatialEntry>> nodeEntryMap = new HashMap<>();
        List<I3SNode> nodes = new ArrayList<>();
        int[] counter = {0};

        I3SNode root = new I3SNode(counter[0]++, 0);
        nodes.add(root);

        subdivide(root, entries, extent,
                formatOptions.getMaxFeaturesPerNode(),
                formatOptions.getMaxTreeDepth(),
                nodes, counter, nodeEntryMap);

        computeLeafBoundingVolumes(nodes, nodeEntryMap);
        updateBoundingVolumes(root);
        setLodThresholds(root);

        return new CellTree(nodes, nodeEntryMap);
    }

    /**
     * Merge multiple cell trees into a single global tree with sequential
     * node indices. Creates a global root node (index 0) whose children
     * are the cell tree roots.
     * <p>
     * For a single cell, the cell tree is still wrapped under a global root
     * to maintain a consistent tree structure.
     */
    static MergedTree mergeIntoGlobalTree(List<CellTree> cellTrees) {
        List<I3SNode> allNodes = new ArrayList<>();
        Map<Integer, List<SpatialEntry>> globalEntryMap = new HashMap<>();
        Set<Integer> meshNodeIndices = new HashSet<>();

        I3SNode globalRoot = new I3SNode(0, 0);
        allNodes.add(globalRoot);

        int nextIndex = 1;
        for (CellTree cell : cellTrees) {
            int offset = nextIndex;

            for (I3SNode node : cell.nodes()) {
                int oldIndex = node.getIndex();
                int newIndex = oldIndex + offset;

                // Transfer entry mapping before reassigning index
                List<SpatialEntry> entries = cell.nodeEntryMap().get(oldIndex);
                if (entries != null) {
                    globalEntryMap.put(newIndex, entries);
                    meshNodeIndices.add(newIndex);
                }

                node.setIndex(newIndex);
                allNodes.add(node);
            }

            // Connect cell root as child of global root
            I3SNode cellRoot = cell.nodes().get(0);
            globalRoot.addChild(cellRoot);

            nextIndex += cell.nodes().size();
        }

        // Compute global root bounding volume from children
        globalRoot.updateBoundingVolume();
        globalRoot.setLodThreshold(INTERNAL_NODE_LOD_THRESHOLD);

        return new MergedTree(allNodes, globalEntryMap, meshNodeIndices);
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
     * Single pass over features per level — O(N) per level.
     */
    private static void subdivide(I3SNode node, List<SpatialEntry> entries,
                                  double[] extent, int maxPerNode, int maxDepth,
                                  List<I3SNode> allNodes, int[] nodeCounter,
                                  Map<Integer, List<SpatialEntry>> nodeEntryMap) {
        if (entries.size() <= maxPerNode || node.getLevel() >= maxDepth) {
            node.setFeatureCount(entries.size());
            nodeEntryMap.put(node.getIndex(), new ArrayList<>(entries));
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

    /**
     * Compute bounding volumes for leaf nodes from their entries' bounding boxes.
     * <p>
     * Relies on the invariant that cell-tree node indices match their position
     * in {@code allNodes}: {@link #buildCellTree} creates nodes via
     * {@code counter[0]++} and appends them to the list in the same order, so
     * {@code allNodes.get(index)} is O(1) and index-stable. Any future change
     * that inserts or reorders nodes in this list must preserve this invariant
     * or switch to an {@code IdentityHashMap<I3SNode, ...>} lookup.
     */
    private static void computeLeafBoundingVolumes(List<I3SNode> allNodes,
                                                   Map<Integer, List<SpatialEntry>> nodeEntryMap) {
        for (Map.Entry<Integer, List<SpatialEntry>> entry : nodeEntryMap.entrySet()) {
            double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, minZ = Double.MAX_VALUE;
            double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
            for (SpatialEntry e : entry.getValue()) {
                double[] bb = e.bbox();
                if (bb[0] < minX) minX = bb[0];
                if (bb[1] < minY) minY = bb[1];
                if (bb[2] < minZ) minZ = bb[2];
                if (bb[3] > maxX) maxX = bb[3];
                if (bb[4] > maxY) maxY = bb[4];
                if (bb[5] > maxZ) maxZ = bb[5];
            }
            I3SNode node = allNodes.get(entry.getKey());
            assert node.getIndex() == entry.getKey()
                    : "Cell-tree node index must match its list position.";
            node.setMbs(BoundingVolume.ofBoundingBox(minX, minY, minZ, maxX, maxY, maxZ));
        }
    }

    private static void updateBoundingVolumes(I3SNode node) {
        for (I3SNode child : node.getChildren()) {
            updateBoundingVolumes(child);
        }
        node.updateBoundingVolume();
    }

    private static void setLodThresholds(I3SNode node) {
        // Leaf: larger threshold → stays visible across a wide range of screen
        //       areas (has the actual geometry and no children to refine to).
        // Internal: smaller threshold → refines to children as soon as the node
        //           takes up meaningful screen area (no geometry of its own).
        boolean isLeaf = node.getChildren().isEmpty();
        node.setLodThreshold(isLeaf ? LEAF_NODE_LOD_THRESHOLD : INTERNAL_NODE_LOD_THRESHOLD);

        for (I3SNode child : node.getChildren()) {
            setLodThresholds(child);
        }
    }
}
