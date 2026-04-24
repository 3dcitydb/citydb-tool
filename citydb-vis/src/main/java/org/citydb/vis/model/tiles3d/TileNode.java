/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences (HFT Stuttgart) <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.model.tiles3d;

import com.alibaba.fastjson2.annotation.JSONType;
import org.citydb.vis.scene.BoundingVolume;
import org.citydb.vis.scene.SceneNode;

import java.util.ArrayList;
import java.util.List;

/**
 * A tile node in the tileset hierarchy. Optional properties are left null
 * and omitted from the output by fastjson2's default null-skipping — this
 * keeps tileset JSON compliant with the 3D Tiles spec (which treats
 * unspecified fields as absent, not as JSON {@code null}) and avoids
 * emitting {@code "content":null} / {@code "children":null} /
 * {@code "transform":null} clutter on every tile.
 */
@JSONType(alphabetic = false)
public class TileNode {
    private TileBoundingVolume boundingVolume;
    private double geometricError;
    private String refine;
    private TileContent content;
    private double[] transform;
    private List<TileNode> children;

    /**
     * Build a tile node from a SceneNode with an optional content URI
     * (null for nodes without their own geometry).
     */
    public static TileNode of(SceneNode node, double geometricError, String contentUri) {
        TileNode tile = new TileNode();
        tile.boundingVolume = TileBoundingVolume.fromBoundingVolume(node.getBoundingVolume());
        tile.geometricError = geometricError;
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
    public static TileNode ofExternalRef(SceneNode node, double geometricError, String uri) {
        TileNode tile = new TileNode();
        tile.boundingVolume = TileBoundingVolume.fromBoundingVolume(node.getBoundingVolume());
        tile.geometricError = geometricError;
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
     * Compute geometric error for a node: {@code R × geRatio} for
     * intermediate nodes, {@code 0} for leaf nodes.
     * <p>
     * {@code geRatio} ties the geometric error to the unified
     * {@code --lod-refine-radius} target via
     * {@code geRatio = 16 / lodRefineRadius}. At Cesium's default runtime
     * {@code maximumScreenSpaceError = 16}, this makes a tile refine when
     * its projected MBS radius exceeds {@code lodRefineRadius} pixels —
     * identical to the I3S refine point at the same parameter.
     */
    public static double computeGeometricError(SceneNode node, double geRatio) {
        if (node.getChildren().isEmpty()) return 0;
        BoundingVolume bv = node.getBoundingVolume();
        return bv != null ? bv.getRadius() * geRatio : 0;
    }
}
