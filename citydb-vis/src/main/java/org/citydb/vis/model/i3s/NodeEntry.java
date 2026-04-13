/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.vis.model.i3s;

import com.alibaba.fastjson2.JSONWriter;
import com.alibaba.fastjson2.annotation.JSONField;
import com.alibaba.fastjson2.annotation.JSONType;
import org.citydb.vis.scene.SceneNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@JSONType(alphabetic = false)
public class NodeEntry {
    private int index;
    private double[] mbs;
    private double lodThreshold;
    private List<Integer> children;
    private int parentIndex;
    @JSONField(serializeFeatures = JSONWriter.Feature.WriteNulls)
    private NodeMesh mesh;
    private int featureCount;

    public static NodeEntry of(SceneNode node, Set<Integer> meshNodeIndices,
                               boolean hasTextures) {
        NodeEntry entry = new NodeEntry();
        entry.index = node.getIndex();

        if (node.getBoundingVolume() != null) {
            entry.mbs = node.getBoundingVolume().toMbs();
        }

        entry.lodThreshold = node.getLodThreshold();

        List<Integer> childIndices = new ArrayList<>(node.getChildren().size());
        for (SceneNode child : node.getChildren()) {
            childIndices.add(child.getIndex());
        }
        entry.children = childIndices;

        entry.parentIndex = node.getParent() != null ? node.getParent().getIndex() : -1;

        if (meshNodeIndices.contains(node.getIndex())) {
            entry.mesh = NodeMesh.of(node, hasTextures);
        }

        entry.featureCount = node.getFeatureCount();
        return entry;
    }
}
