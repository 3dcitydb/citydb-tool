/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences (HFT Stuttgart) <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.model.i3s;

import com.alibaba.fastjson2.annotation.JSONType;
import org.citydb.vis.scene.SceneNode;

@JSONType(alphabetic = false)
public class NodeMesh {
    private MeshMaterial material;
    private MeshGeometry geometry;
    private MeshAttribute attribute;

    public static NodeMesh of(SceneNode node, boolean hasTextures) {
        boolean nodeHasTexture = hasTextures && node.hasTexture();
        int definition = nodeHasTexture
                ? SceneLayerDescriptor.TEXTURED_DEFINITION_INDEX
                : SceneLayerDescriptor.UNTEXTURED_DEFINITION_INDEX;
        // ArcGIS requires non-negative resource index. Use the node's own
        // resource index (same as geometry) for both textured and untextured.
        int materialResource = node.getIndex();

        NodeMesh mesh = new NodeMesh();
        // texelCountHint: width × height of the texture atlas — required by
        // ArcGIS for textured materials, omitted for untextured nodes.
        Integer texelHint = nodeHasTexture ? node.getTexelCountHint() : null;
        mesh.material = new MeshMaterial(definition, materialResource, texelHint);
        mesh.geometry = new MeshGeometry(definition, node.getIndex(),
                node.getOutputVertexCount(), node.getFeatureCount());
        mesh.attribute = new MeshAttribute(node.getIndex());
        return mesh;
    }
}
