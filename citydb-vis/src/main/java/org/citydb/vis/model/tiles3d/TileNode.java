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
     * <p>
     * Refinement is {@code REPLACE} for every tile: children take over from
     * the parent once their content meets the screen-space error. LOD-preview
     * cell roots ({@code node.isLodPreview()}) need this so the low-resolution
     * preview is hidden when the high-resolution split leaves load;
     * content-less intermediates (aggregation, mixed-split, split depth>1)
     * also use REPLACE so the runtime never renders them alongside their
     * descendants.
     */
    public static TileNode of(SceneNode node, double geometricError, String contentUri) {
        TileNode tile = new TileNode();
        tile.boundingVolume = TileBoundingVolume.fromBoundingVolume(node.getBoundingVolume());
        tile.geometricError = geometricError;
        tile.refine = "REPLACE";

        if (contentUri != null) {
            tile.content = new TileContent(contentUri);
        }

        if (!node.getChildren().isEmpty()) {
            tile.children = new ArrayList<>(node.getChildren().size());
        }

        return tile;
    }

    /**
     * Create a tile node that references an external subtileset. Always
     * emits {@code refine=REPLACE} so the parent tileset's view of this
     * tile matches the external subtree's intent regardless of how the
     * runtime resolves external-ref refinement (some implementations use
     * the external-ref tile's refine, others use the loaded subtree
     * root's refine — setting it explicitly here keeps both code paths
     * consistent).
     */
    public static TileNode ofExternalRef(SceneNode node, double geometricError, String uri) {
        TileNode tile = new TileNode();
        tile.boundingVolume = TileBoundingVolume.fromBoundingVolume(node.getBoundingVolume());
        tile.geometricError = geometricError;
        tile.content = new TileContent(uri);
        tile.refine = "REPLACE";
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
        tile.refine = "REPLACE";
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
     * {@code --screen-pixel-threshold} target via
     * {@code geRatio = 16 / screenPixelThreshold}. At Cesium's default runtime
     * {@code maximumScreenSpaceError = 16}, this makes a tile refine when
     * its projected MBS radius exceeds {@code screenPixelThreshold} pixels —
     * identical to the I3S refine point at the same parameter.
     * <p>
     * When {@code screenPixelThreshold == 0} the caller passes
     * {@code POSITIVE_INFINITY} as {@code geRatio}; we collapse that to
     * {@link #ALWAYS_REFINE_GE} (a finite, JSON-safe huge value) so Cesium
     * always sees SSE above its threshold and refines into the leaves.
     */
    public static double computeGeometricError(SceneNode node, double geRatio) {
        if (node.getChildren().isEmpty()) return 0;
        BoundingVolume bv = node.getBoundingVolume();
        if (bv == null) return 0;
        if (Double.isInfinite(geRatio)) return ALWAYS_REFINE_GE;
        return bv.getRadius() * geRatio;
    }

    private static final double ALWAYS_REFINE_GE = 1.0e10;
}
