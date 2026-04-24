/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences (HFT Stuttgart) <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.scene;

import org.citydb.vis.store.NodeEntry;
import org.citydb.vis.store.NodeEntryStore;
import org.citydb.vis.store.SpatialEntry;
import org.citydb.vis.util.BoundingBoxUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds scene node hierarchies using a one-level spatial indexing strategy:
 * <ol>
 *   <li><b>Grid partitioning</b> (in {@link org.citydb.vis.pipeline.stages.PartitioningStage}):
 *       features are classified into spatial grid cells via
 *       {@link org.citydb.vis.store.PartitionedEntryStore}. Cell size is
 *       driven entirely by the configured {@code --grid-edge-length}.</li>
 *   <li><b>Cell = leaf</b> (here): each populated grid cell becomes a single
 *       leaf {@link SceneNode} holding all its features. The 3D Tiles
 *       {@link org.citydb.vis.scene.CellAggregator} sits above
 *       these leaves to provide the hierarchical structure.</li>
 * </ol>
 * After each cell leaf is built,
 * {@link org.citydb.vis.pipeline.stages.TreeBuildingStage} remaps its node
 * index into the global tree and flushes the compact {@link NodeEntry}
 * list to disk via {@link org.citydb.vis.store.NodeEntryStore}.
 * <p>
 * This builder is format-agnostic — it produces a spatial hierarchy of
 * {@link SceneNode} objects without applying format-specific properties
 * such as LOD thresholds or geometric errors. The caller is responsible
 * for setting those after the tree is built.
 */
public class NodeBuilder {

    /**
     * A single cell leaf: the {@link SceneNode} with its bounding volume
     * already set, and the compact {@link NodeEntry} list ready to flush
     * to {@link NodeEntryStore}. The {@link SpatialEntry} spatial fields
     * (centerX/Y/bbox) are consumed during construction and dropped.
     */
    public record CellLeaf(SceneNode node, List<NodeEntry> entries) {
    }

    /**
     * Build a single leaf node containing all entries in a grid cell.
     * The returned {@link SceneNode} has index {@code 0}; the caller
     * remaps it to global scope before flushing.
     * <p>
     * Thread-safe — no shared mutable state. Can be called concurrently
     * for different cells.
     */
    public static CellLeaf buildCellLeaf(List<SpatialEntry> entries) {
        SceneNode leaf = new SceneNode(0, 0);
        leaf.setFeatureCount(entries.size());

        double[] acc = BoundingBoxUtils.emptyAabb();
        List<NodeEntry> compact = new ArrayList<>(entries.size());
        for (SpatialEntry e : entries) {
            BoundingBoxUtils.expandToBox(acc, e.bbox());
            compact.add(new NodeEntry(e.id(), e.meshHandle(), e.attrOffset()));
        }
        leaf.setBoundingVolume(BoundingVolume.ofBoundingBox(
                acc[0], acc[1], acc[2], acc[3], acc[4], acc[5]));

        return new CellLeaf(leaf, compact);
    }

    /**
     * Set the bounding volume on the global root node after all cell leaves
     * have been attached as children.
     */
    public static void finalizeGlobalRoot(SceneNode globalRoot) {
        globalRoot.updateBoundingVolume();
    }
}
