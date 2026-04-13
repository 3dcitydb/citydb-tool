/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.vis.model.tiles3d;

import com.alibaba.fastjson2.JSONWriter;
import com.alibaba.fastjson2.annotation.JSONField;
import com.alibaba.fastjson2.annotation.JSONType;
import org.citydb.vis.scene.BoundingVolume;
import org.citydb.vis.scene.SceneNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * A tile node in the tileset hierarchy.
 */
@JSONType(alphabetic = false)
public class TileNode {
    TileBoundingVolume boundingVolume;
    double geometricError;
    String refine;
    @JSONField(serializeFeatures = JSONWriter.Feature.WriteNulls)
    TileContent content;
    @JSONField(serializeFeatures = JSONWriter.Feature.WriteNulls)
    double[] transform;
    @JSONField(serializeFeatures = JSONWriter.Feature.WriteNulls)
    List<TileNode> children;

    /**
     * Build a tile node from a SceneNode, inlining children in the same
     * page and emitting external references for split subtrees.
     *
     * @param overrideGeo if &ge; 0, override computed geometric error
     */
    public static TileNode of(SceneNode node, Set<Integer> meshNodeIndices,
                              double overrideGeo) {
        TileNode tile = new TileNode();
        tile.boundingVolume = TileBoundingVolume.fromMbs(node.getBoundingVolume());
        tile.geometricError = overrideGeo >= 0 ? overrideGeo : computeGeometricError(node);
        tile.refine = "ADD";

        if (meshNodeIndices.contains(node.getIndex())) {
            tile.content = new TileContent("../tiles/" + node.getIndex() + ".glb");
        }

        if (!node.getChildren().isEmpty()) {
            tile.children = new ArrayList<>(node.getChildren().size());
        }

        return tile;
    }

    /**
     * Create a tile node that references an external subtileset.
     */
    public static TileNode ofExternalRef(SceneNode node, String uri) {
        TileNode tile = new TileNode();
        tile.boundingVolume = TileBoundingVolume.fromMbs(node.getBoundingVolume());
        tile.geometricError = computeGeometricError(node);
        tile.content = new TileContent(uri);
        return tile;
    }

    public void addChild(TileNode child) {
        if (children == null) {
            children = new ArrayList<>();
        }
        children.add(child);
    }

    /**
     * Compute geometric error for a node: {@code R/16} for intermediate
     * nodes, {@code 0} for leaf nodes.
     */
    public static double computeGeometricError(SceneNode node) {
        if (node.getChildren().isEmpty()) return 0;
        BoundingVolume bv = node.getBoundingVolume();
        return bv != null ? bv.getRadius() / 16.0 : 0;
    }
}
