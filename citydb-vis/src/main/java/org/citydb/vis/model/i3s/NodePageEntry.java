/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.vis.model.i3s;

import com.alibaba.fastjson2.JSONWriter;
import com.alibaba.fastjson2.annotation.JSONField;
import com.alibaba.fastjson2.annotation.JSONType;
import org.citydb.vis.scene.BoundingVolume;
import org.citydb.vis.scene.SceneNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Single entry in an I3S node page, serialized to {@code nodepages/N/index.json}.
 * <p>
 * ArcGIS Pro compatibility requirements:
 * <ul>
 *   <li>{@code obb} (Oriented Bounding Box) is required — MBS alone is rejected.</li>
 *   <li>{@code parentIndex} must be omitted for the root node (value {@code -1}
 *       triggers "Value is not convertible to Int").</li>
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
    @JSONField(serializeFeatures = JSONWriter.Feature.WriteNulls)
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

        List<Integer> childIndices = new ArrayList<>(node.getChildren().size());
        for (SceneNode child : node.getChildren()) {
            childIndices.add(child.getIndex());
        }
        entry.children = childIndices;

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
