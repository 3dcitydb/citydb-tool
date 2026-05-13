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
 * Bounding volume is OBB only — never co-emit {@code mbs}, or ArcGIS JS
 * picks misfire at oblique angles (uses MBS for broad-phase, OBB for LOD,
 * and the disagreement drops pick rays). OBB quaternion comes from
 * {@link BoundingVolume#toObbQuaternion()}.
 * <p>
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
    private int lodThreshold;
    private List<Integer> children;
    private Integer parentIndex;
    private NodeMesh mesh;
    private int featureCount;

    public static NodePageEntry of(SceneNode node, Set<Integer> meshNodeIndices,
                                   boolean hasTextures) {
        NodePageEntry entry = new NodePageEntry();
        entry.index = node.getIndex();

        BoundingVolume bv = node.getBoundingVolume();
        if (bv != null) {
            entry.obb = new Obb(bv.toObbCenter(), bv.toObbHalfSize(), bv.toObbQuaternion());
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
     * I3S Oriented Bounding Box. {@code center} is {@code (lon°, lat°, alt m)};
     * {@code halfSize} is meters along the box's local ENU axes
     * (east / north / up); {@code quaternion} is the ENU→ECEF rotation at
     * {@code center}, supplied by {@link BoundingVolume#toObbQuaternion()}.
     */
    @JSONType(alphabetic = false)
    public record Obb(double[] center, double[] halfSize, double[] quaternion) {
    }
}
