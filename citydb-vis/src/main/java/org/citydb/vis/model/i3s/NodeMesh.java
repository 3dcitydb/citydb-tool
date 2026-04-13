/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
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
        int materialResource = nodeHasTexture ? node.getIndex() : -1;

        NodeMesh mesh = new NodeMesh();
        mesh.material = new MeshMaterial(definition, materialResource);
        mesh.geometry = new MeshGeometry(definition, node.getIndex(),
                node.getOutputVertexCount(), node.getFeatureCount());
        mesh.attribute = new MeshAttribute(node.getIndex());
        return mesh;
    }
}
