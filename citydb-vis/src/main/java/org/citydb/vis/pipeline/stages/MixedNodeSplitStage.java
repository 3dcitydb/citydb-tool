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
 * Split every mesh node that carries both textured and untextured features
 * into two child nodes — one holding the textured features, one holding the
 * untextured features. The original node becomes an intermediate node with
 * no mesh content; its bbox and position in the tree are preserved. Both
 * children inherit the original's bounding volume (the split is a per-feature
 * reclassification, not a spatial subdivision).
 * <p>
 * Each resulting mesh node is therefore homogeneous: downstream encoders
 * see either a fully-textured or a fully-untextured mesh and can pick the
 * matching geometry definition and material without per-feature
 * compromises. This is the only way to give untextured features a real
 * non-textured material in I3S, where every node has exactly one material.
 * 3D Tiles also benefits: the two children emit separate GLBs each with
 * the appropriate material, rather than relying on the multi-primitive
 * split inside a single GLB.
 * <p>
 * Push-down (rather than sibling) split keeps the cell root in place with
 * its grid coordinates intact, so the 3D Tiles
 * {@link org.citydb.vis.scene.CellAggregator} continues to index
 * cells correctly regardless of how far the mesh moves into the subtree.
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

        // Snapshot the original index set — we append new nodes during the loop.
        List<Integer> originals = new ArrayList<>(meshNodeIndices);
        Set<Integer> updatedMeshIndices = new HashSet<>(meshNodeIndices);
        int nextIndex = allNodes.size();
        int splitCount = 0;

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
                int childLevel = original.getLevel() + 1;

                SceneNode texturedChild = new SceneNode(nextIndex, childLevel);
                texturedChild.setBoundingVolume(original.getBoundingVolume());
                texturedChild.setFeatureCount(textured.size());
                original.addChild(texturedChild);
                allNodes.add(texturedChild);
                nodeEntryStore.writeNode(nextIndex, textured);
                updatedMeshIndices.add(nextIndex);
                nextIndex++;

                SceneNode untexturedChild = new SceneNode(nextIndex, childLevel);
                untexturedChild.setBoundingVolume(original.getBoundingVolume());
                untexturedChild.setFeatureCount(untextured.size());
                original.addChild(untexturedChild);
                allNodes.add(untexturedChild);
                nodeEntryStore.writeNode(nextIndex, untextured);
                updatedMeshIndices.add(nextIndex);
                nextIndex++;

                // Original keeps its bbox and aggregate feature count but stops
                // carrying mesh data. Its on-disk NodeEntry list is orphaned —
                // acceptable since the node entry file is a short-lived temp
                // store and the offset pointer is simply never read again.
                updatedMeshIndices.remove(nodeIndex);
                splitCount++;
            }
        } catch (IOException e) {
            throw new VisExportException("Failed to split mixed-texture nodes.", e);
        }

        if (splitCount > 0) {
            logger.info("Pushed down {} mixed-texture node(s); each now has a textured and " +
                            "an untextured child holding the respective features.",
                    splitCount);
            ctx.setMeshNodeIndices(updatedMeshIndices);
        }
    }
}
