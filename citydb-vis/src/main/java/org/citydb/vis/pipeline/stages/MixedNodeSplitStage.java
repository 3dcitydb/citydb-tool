/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences (HFT Stuttgart) <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.pipeline.stages;

import org.citydb.vis.pipeline.PipelineContext;
import org.citydb.vis.pipeline.Stage;
import org.citydb.vis.scene.SceneNode;
import org.citydb.vis.store.NodeEntry;
import org.citydb.vis.store.NodeEntryStore;
import org.citydb.vis.store.VisExportStores;
import org.citydb.vis.VisExportException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Split every scene node that carries both textured and untextured features
 * into two sibling nodes, one holding the textured features and one holding
 * the untextured features. Each resulting node is therefore homogeneous:
 * downstream encoders see either a fully-textured or a fully-untextured
 * mesh and can pick the matching geometry definition and material without
 * per-feature compromises (which is the only way to give untextured
 * features real PBR lighting in I3S, where every node has exactly one
 * material).
 * <p>
 * The textured partition stays under the original node index; the
 * untextured partition is appended as a new child of the same parent with
 * a fresh index. Both share the original bounding volume — the split is a
 * per-feature reclassification, not a spatial subdivision, and splitting
 * the bbox by feature membership would push ArcGIS Pro's obb validation
 * over the edge on slender facade geometry.
 * <p>
 * Run after {@link TreeBuildingStage}: the tree structure and per-node
 * entries must exist, and the per-feature texture flags populated by
 * {@link org.citydb.vis.writer.VisWriter} on the caller thread during the
 * write phase are consumed here to classify each {@link NodeEntry}.
 */
public final class MixedNodeSplitStage implements Stage {
    private final Logger logger = LoggerFactory.getLogger(MixedNodeSplitStage.class);

    @Override
    public void execute(PipelineContext ctx) throws VisExportException {
        VisExportStores stores = ctx.stores();
        NodeEntryStore nodeEntryStore = stores.getNodeEntryStore();
        List<SceneNode> allNodes = ctx.allNodes();
        Set<Integer> meshNodeIndices = ctx.meshNodeIndices();
        Set<Integer> cellRootIndices = ctx.cellRootGridCoords() != null
                ? ctx.cellRootGridCoords().keySet() : Set.of();

        // Snapshot the original index set — we append new nodes during the loop.
        List<Integer> originals = new ArrayList<>(meshNodeIndices);
        Set<Integer> updatedMeshIndices = new HashSet<>(meshNodeIndices);
        int nextIndex = allNodes.size();
        int splitCount = 0;
        int cellRootSkipCount = 0;

        try {
            for (int nodeIndex : originals) {
                List<NodeEntry> entries = nodeEntryStore.loadNode(nodeIndex);
                if (entries.isEmpty()) {
                    continue;
                }

                List<NodeEntry> textured = new ArrayList<>();
                List<NodeEntry> untextured = new ArrayList<>();
                for (NodeEntry entry : entries) {
                    if (stores.isFeatureTextured(entry.id())) {
                        textured.add(entry);
                    } else {
                        untextured.add(entry);
                    }
                }
                if (textured.isEmpty() || untextured.isEmpty()) {
                    continue;
                }

                SceneNode original = allNodes.get(nodeIndex);
                SceneNode parent = original.getParent();
                if (parent == null) {
                    // Global root is never a mesh node in practice; guard defensively.
                    continue;
                }

                // Cell roots are direct children of the global root. Adding a
                // sibling here would put a node under globalRoot without grid
                // coords, which the 3D Tiles CellAggregator indexes by cell —
                // so leave these rare leaf cell roots to the intra-node
                // white-pixel fallback in the atlas. Expected to be uncommon:
                // cell roots are leaves only when the cell holds fewer than
                // maxFeaturesPerNode features.
                if (cellRootIndices.contains(nodeIndex)) {
                    cellRootSkipCount++;
                    continue;
                }

                SceneNode sibling = new SceneNode(nextIndex, original.getLevel());
                sibling.setBoundingVolume(original.getBoundingVolume());
                sibling.setFeatureCount(untextured.size());
                parent.addChild(sibling);
                allNodes.add(sibling);

                // Overwriting the original index orphans its old on-disk entries
                // but updates the offset pointer — acceptable since the node entry
                // file is a short-lived temp store.
                nodeEntryStore.writeNode(nodeIndex, textured);
                nodeEntryStore.writeNode(nextIndex, untextured);
                original.setFeatureCount(textured.size());

                updatedMeshIndices.add(nextIndex);
                nextIndex++;
                splitCount++;
            }
        } catch (IOException e) {
            throw new VisExportException("Failed to split mixed-texture nodes.", e);
        }

        if (splitCount > 0) {
            logger.info("Split {} mixed-texture nodes into per-texture sibling pairs.",
                    splitCount);
            ctx.setMeshNodeIndices(updatedMeshIndices);
        }
        if (cellRootSkipCount > 0) {
            logger.info("Left {} mixed-texture leaf cell root(s) unsplit; " +
                    "untextured triangles will sample the atlas white pixel.",
                    cellRootSkipCount);
        }
    }
}
