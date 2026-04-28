/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences (HFT Stuttgart) <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.model.i3s;

import com.alibaba.fastjson2.annotation.JSONType;
import org.citydb.vis.scene.BoundingVolume;
import org.citydb.vis.scene.SceneNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Single entry in an I3S node page, serialized to {@code nodepages/N/index.json}.
 * <p>
 * Bounding volume choice is target-dependent and mutually exclusive between
 * the two I3S 1.7 runtime families:
 * <ul>
 *   <li>ArcGIS (Pro via SLPK, Maps SDK for JavaScript and Online Scene Viewer
 *       via folder with {@code --obb}): {@code obb} is required — without it
 *       the layer loads but renders nothing in the web SDKs, and ArcGIS Pro
 *       rejects the SLPK outright (I3S 1.7 schema requirement).</li>
 *   <li>CesiumJS (folder, default): only {@code mbs} — CesiumJS's I3S OBB
 *       handling mis-culls buildings at certain camera angles, so OBB is
 *       suppressed unless the user opts in via {@code --obb}.</li>
 * </ul>
 * Gating lives in {@code I3SWriter.writeOutput()} as {@code slpk || --obb}.
 * Other quirks:
 * <ul>
 *   <li>{@code parentIndex} must be omitted for the root node (value {@code -1}
 *       triggers ArcGIS Pro's "Value is not convertible to Int").</li>
 *   <li>{@code lodThreshold} must serialize as integer, not float.</li>
 *   <li>{@code material.resource} must be non-negative.</li>
 * </ul>
 */
@JSONType(alphabetic = false)
public class NodePageEntry {
    private int index;
    private Obb obb;
    private double[] mbs;
    private int lodThreshold;
    private List<Integer> children;
    private Integer parentIndex;
    private NodeMesh mesh;
    private int featureCount;

    public static NodePageEntry of(SceneNode node, Set<Integer> meshNodeIndices,
                                   boolean hasTextures, boolean includeObb) {
        NodePageEntry entry = new NodePageEntry();
        entry.index = node.getIndex();

        BoundingVolume bv = node.getBoundingVolume();
        if (bv != null) {
            // MBS and OBB are mutually exclusive: emitting both makes the
            // ArcGIS Maps SDK for JavaScript use MBS for broad-phase culling
            // but OBB for LOD selection, and the slight disagreement between
            // the two volumes causes feature pick rays to miss at oblique
            // camera angles even though rendering is fine.
            if (includeObb) {
                entry.obb = new Obb(bv.toObbCenter(), bv.toObbHalfSize(), bv.toObbQuaternion());
            } else {
                entry.mbs = bv.toMbs();
            }
        }

        entry.lodThreshold = node.getLodThreshold();

        // Omit children for leaf nodes (null → not serialized by fastjson2).
        // Esri's own I3S samples follow this convention; ArcGIS treats an
        // explicit empty array as a non-leaf with zero children, which is
        // semantically different from "this is a leaf".
        List<SceneNode> nodeChildren = node.getChildren();
        if (!nodeChildren.isEmpty()) {
            List<Integer> childIndices = new ArrayList<>(nodeChildren.size());
            for (SceneNode child : nodeChildren) {
                childIndices.add(child.getIndex());
            }
            entry.children = childIndices;
        }

        // Omit parentIndex for root node (null → not serialized by fastjson2).
        // ArcGIS rejects -1 as "not convertible to Int".
        entry.parentIndex = node.getParent() != null
                ? node.getParent().getIndex() : null;

        if (meshNodeIndices.contains(node.getIndex())) {
            entry.mesh = NodeMesh.of(node, hasTextures);
        }

        entry.featureCount = node.getFeatureCount();
        return entry;
    }

    /**
     * I3S Oriented Bounding Box. For axis-aligned bounding boxes the
     * quaternion is the identity {@code [0, 0, 0, 1]}.
     */
    @JSONType(alphabetic = false)
    public record Obb(double[] center, double[] halfSize, double[] quaternion) {
    }
}
