/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.vis.scene;

import org.citydb.vis.geometry.TriangleMesh;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a node in a visualization scene hierarchy (bounding volume
 * hierarchy). Used by all visualization export formats (I3S, 3D Tiles, etc.).
 * <p>
 * The hierarchy is built using the grid-based tiling strategy from the
 * 3DCityDB VIS plugin, with per-cell quadtree subdivision.
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
    private int textureId = -1;
    private int texelCountHint;

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

    public SceneNode setTextureId(int textureId) {
        this.textureId = textureId;
        return this;
    }

    public boolean hasTexture() {
        return textureId >= 0;
    }

    public int getTexelCountHint() {
        return texelCountHint;
    }

    public SceneNode setTexelCountHint(int texelCountHint) {
        this.texelCountHint = texelCountHint;
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
