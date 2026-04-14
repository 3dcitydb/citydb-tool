/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.vis.model.i3s;

import org.citydb.vis.scene.SceneNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class NodePage {
    private List<NodePageEntry> nodes;

    public static NodePage of(List<SceneNode> pageNodes, Set<Integer> meshNodeIndices,
                              boolean hasTextures) {
        NodePage page = new NodePage();
        page.nodes = new ArrayList<>(pageNodes.size());
        for (SceneNode node : pageNodes) {
            page.nodes.add(NodePageEntry.of(node, meshNodeIndices, hasTextures));
        }
        return page;
    }
}
