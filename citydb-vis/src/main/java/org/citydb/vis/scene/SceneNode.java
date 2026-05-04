/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences (HFT Stuttgart) <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.scene;

import org.citydb.vis.geometry.TriangleMesh;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a node in a visualization scene hierarchy (bounding volume
 * hierarchy). Used by all visualization export formats (I3S, 3D Tiles, etc.).
 * <p>
 * The hierarchy is built with an edge-length-driven grid: each populated
 * grid cell becomes a single leaf scene node. Format-specific layers (e.g.,
 * the 3D Tiles {@link org.citydb.vis.scene.CellAggregator}) may
 * wrap the cell roots in additional structure above the global root.
 */
public class SceneNode {
    private int index;
    private final int level;
    private BoundingVolume boundingVolume;
    private SceneNode parent;
    private final List<SceneNode> children;
    private TriangleMesh mesh;
    private int featureCount;
    private int lodThreshold;
    private int outputVertexCount;
    private boolean textured;
    private int texelCountHint;
    private boolean lodPreview;
    private boolean colored;
    private boolean coloredBlend;
    private boolean hasStyleOverride;

    public SceneNode(int index, int level) {
        this.index = index;
        this.level = level;
        this.children = new ArrayList<>();
    }

    public int getIndex() {
        return index;
    }

    public SceneNode setIndex(int index) {
        this.index = index;
        return this;
    }

    public int getLevel() {
        return level;
    }

    public BoundingVolume getBoundingVolume() {
        return boundingVolume;
    }

    public SceneNode setBoundingVolume(BoundingVolume boundingVolume) {
        this.boundingVolume = boundingVolume;
        return this;
    }

    public SceneNode getParent() {
        return parent;
    }

    public SceneNode setParent(SceneNode parent) {
        this.parent = parent;
        return this;
    }

    public List<SceneNode> getChildren() {
        return children;
    }

    public SceneNode addChild(SceneNode child) {
        children.add(child);
        child.setParent(this);
        return this;
    }

    public TriangleMesh getMesh() {
        return mesh;
    }

    public SceneNode setMesh(TriangleMesh mesh) {
        this.mesh = mesh;
        return this;
    }

    public int getFeatureCount() {
        return featureCount;
    }

    public SceneNode setFeatureCount(int featureCount) {
        this.featureCount = featureCount;
        return this;
    }

    public int getLodThreshold() {
        return lodThreshold;
    }

    public SceneNode setLodThreshold(int lodThreshold) {
        this.lodThreshold = lodThreshold;
        return this;
    }

    public int getOutputVertexCount() {
        return outputVertexCount;
    }

    public SceneNode setOutputVertexCount(int count) {
        this.outputVertexCount = count;
        return this;
    }

    public SceneNode setTextured(boolean textured) {
        this.textured = textured;
        return this;
    }

    public boolean hasTexture() {
        return textured;
    }

    public SceneNode setColored(boolean colored) {
        this.colored = colored;
        return this;
    }

    public boolean isColored() {
        return colored;
    }

    /**
     * For colored nodes: whether any baked vertex carries alpha &lt; 1, which
     * promotes the I3S MaterialDefinition from OPAQUE to BLEND.
     */
    public SceneNode setColoredBlend(boolean coloredBlend) {
        this.coloredBlend = coloredBlend;
        return this;
    }

    public boolean isColoredBlend() {
        return coloredBlend;
    }

    /**
     * Whether at least one triangle in this node resolves to a non-default
     * style via the {@link org.citydb.vis.styling.ObjectStyleRegistry}.
     * Set by the I3S geometry encoder during per-triangle color resolution.
     * When {@code true}, the node's NodeMesh is routed to the styled-colored
     * material slot (NORMAL + COLOR_0, shaded). When {@code false}, the
     * node uses the existing untextured or X3DMaterial-colored slots.
     */
    public SceneNode setHasStyleOverride(boolean hasStyleOverride) {
        this.hasStyleOverride = hasStyleOverride;
        return this;
    }

    public boolean hasStyleOverride() {
        return hasStyleOverride;
    }

    public int getTexelCountHint() {
        return texelCountHint;
    }

    public SceneNode setTexelCountHint(int texelCountHint) {
        this.texelCountHint = texelCountHint;
        return this;
    }

    /**
     * Whether this node carries a low-resolution LOD preview of its descendants:
     * an atlas rescaled to fit {@code --max-atlas-size} for fast initial load,
     * to be replaced by the higher-resolution children when the runtime refines
     * to them. Set by {@link org.citydb.vis.pipeline.stages.AtlasOverflowQuadtreeStage}
     * on cell roots that got spatially split, but only when
     * {@code --atlas-overflow-mode=hybrid}; the pure {@code quadtree} mode
     * drops the cell root from {@code meshNodeIndices} entirely instead.
     * <p>
     * Consumed by:
     * <ul>
     *   <li>{@code VisWriter.prepareNodeMesh}: forces single-atlas
     *       {@code RESCALE} fallback regardless of the user's
     *       {@code --atlas-fallback} so the preview always fits one page.</li>
     *   <li>{@code TilesetSerializer} (3D Tiles): emits {@code refine=REPLACE}
     *       so children replace this tile when loaded (rather than ADD,
     *       which would render both at the same time).</li>
     * </ul>
     */
    public boolean isLodPreview() {
        return lodPreview;
    }

    public SceneNode setLodPreview(boolean lodPreview) {
        this.lodPreview = lodPreview;
        return this;
    }

    public void updateBoundingVolume() {
        if (mesh != null && !mesh.isEmpty()) {
            double[] bbox = mesh.computeBoundingBox();
            boundingVolume = BoundingVolume.ofBoundingBox(bbox[0], bbox[1], bbox[2], bbox[3], bbox[4], bbox[5]);
        }

        for (SceneNode child : children) {
            if (child.boundingVolume != null && boundingVolume != null) {
                boundingVolume = boundingVolume.merge(child.boundingVolume);
            } else if (child.boundingVolume != null) {
                boundingVolume = child.boundingVolume;
            }
        }
    }
}
