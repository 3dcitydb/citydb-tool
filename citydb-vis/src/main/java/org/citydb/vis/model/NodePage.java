/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.vis.model;

import com.alibaba.fastjson2.JSONWriter;
import com.alibaba.fastjson2.annotation.JSONField;
import com.alibaba.fastjson2.annotation.JSONType;
import org.citydb.vis.scene.SceneNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class NodePage {
    private List<NodeEntry> nodes;

    public static NodePage of(List<SceneNode> pageNodes, Set<Integer> meshNodeIndices,
                              boolean hasTextures) {
        NodePage page = new NodePage();
        page.nodes = new ArrayList<>(pageNodes.size());
        for (SceneNode node : pageNodes) {
            page.nodes.add(NodeEntry.of(node, meshNodeIndices, hasTextures));
        }
        return page;
    }

    @JSONType(alphabetic = false)
    public static class NodeEntry {
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

    @JSONType(alphabetic = false)
    public static class NodeMesh {
        private MeshMaterial material;
        private MeshGeometry geometry;
        private MeshAttribute attribute;

        public static NodeMesh of(SceneNode node, boolean hasTextures) {
            boolean nodeHasTexture = hasTextures && node.hasTexture();
            int definition = nodeHasTexture
                    ? SceneLayerDescriptor.TEXTURED_DEFINITION_INDEX
                    : SceneLayerDescriptor.UNTEXTURED_DEFINITION_INDEX;
            int materialResource = nodeHasTexture ? node.getIndex() : -1;

            NodeMesh mesh = new NodeMesh();
            mesh.material = new MeshMaterial(definition, materialResource);
            mesh.geometry = new MeshGeometry(definition, node.getIndex(),
                    node.getOutputVertexCount(), node.getFeatureCount());
            mesh.attribute = new MeshAttribute(node.getIndex());
            return mesh;
        }
    }

    @JSONType(alphabetic = false)
    public record MeshMaterial(int definition, int resource) {
    }

    @JSONType(alphabetic = false)
    public record MeshGeometry(int definition, int resource, int vertexCount, int featureCount) {
    }

    public record MeshAttribute(int resource) {
    }
}
