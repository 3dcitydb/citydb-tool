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

/**
 * A tile node in the tileset hierarchy.
 */
@JSONType(alphabetic = false)
public class TileNode {
    private TileBoundingVolume boundingVolume;
    private double geometricError;
    private String refine;
    @JSONField(serializeFeatures = JSONWriter.Feature.WriteNulls)
    private TileContent content;
    @JSONField(serializeFeatures = JSONWriter.Feature.WriteNulls)
    private double[] transform;
    @JSONField(serializeFeatures = JSONWriter.Feature.WriteNulls)
    private List<TileNode> children;

    /**
     * Build a tile node from a SceneNode with an optional content URI
     * (null for nodes without their own geometry).
     *
     * @param overrideGeo if &ge; 0, override computed geometric error
     */
    public static TileNode of(SceneNode node, double overrideGeo, String contentUri) {
        TileNode tile = new TileNode();
        tile.boundingVolume = TileBoundingVolume.fromBoundingVolume(node.getBoundingVolume());
        tile.geometricError = overrideGeo >= 0 ? overrideGeo : computeGeometricError(node);
        tile.refine = "ADD";

        if (contentUri != null) {
            tile.content = new TileContent(contentUri);
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
        tile.boundingVolume = TileBoundingVolume.fromBoundingVolume(node.getBoundingVolume());
        tile.geometricError = computeGeometricError(node);
        tile.content = new TileContent(uri);
        return tile;
    }

    /**
     * Create a root tile with transform and child slots.
     */
    static TileNode ofRoot(TileBoundingVolume boundingVolume, double geometricError,
                           double[] transform) {
        TileNode tile = new TileNode();
        tile.boundingVolume = boundingVolume;
        tile.geometricError = geometricError;
        tile.refine = "ADD";
        tile.transform = transform;
        tile.children = new ArrayList<>();
        return tile;
    }

    /**
     * Create a content-only tile (no children, no refine).
     */
    static TileNode ofContent(TileBoundingVolume boundingVolume, double geometricError,
                              String uri) {
        TileNode tile = new TileNode();
        tile.boundingVolume = boundingVolume;
        tile.geometricError = geometricError;
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
     * Compute geometric error for a node: {@code R/8} for intermediate
     * nodes, {@code 0} for leaf nodes.
     */
    public static double computeGeometricError(SceneNode node) {
        if (node.getChildren().isEmpty()) return 0;
        BoundingVolume bv = node.getBoundingVolume();
        return bv != null ? bv.getRadius() / 8.0 : 0;
    }
}
