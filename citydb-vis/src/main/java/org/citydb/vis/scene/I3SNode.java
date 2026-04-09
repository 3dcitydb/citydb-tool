/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.vis.scene;

import org.citydb.vis.geometry.TriangleMesh;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a node in the I3S Bounding Volume Hierarchy.
 * Each node contains geometry data (triangle mesh) and may have child nodes.
 * The hierarchy is built using the grid-based tiling strategy from the
 * 3DCityDB VIS plugin, adapted to I3S node pages.
 */
public class I3SNode {
    private int index;
    private final int level;
    private BoundingVolume mbs;
    private I3SNode parent;
    private final List<I3SNode> children;
    private TriangleMesh mesh;
    private int featureCount;
    private double lodThreshold;
    private int outputVertexCount = -1;
    private int textureId = -1;

    public I3SNode(int index, int level) {
        this.index = index;
        this.level = level;
        this.children = new ArrayList<>();
        this.mesh = new TriangleMesh();
    }

    public int getIndex() {
        return index;
    }

    public I3SNode setIndex(int index) {
        this.index = index;
        return this;
    }

    public int getLevel() {
        return level;
    }

    public BoundingVolume getMbs() {
        return mbs;
    }

    public I3SNode setMbs(BoundingVolume mbs) {
        this.mbs = mbs;
        return this;
    }

    public I3SNode getParent() {
        return parent;
    }

    public I3SNode setParent(I3SNode parent) {
        this.parent = parent;
        return this;
    }

    public List<I3SNode> getChildren() {
        return children;
    }

    public I3SNode addChild(I3SNode child) {
        children.add(child);
        child.setParent(this);
        return this;
    }

    public TriangleMesh getMesh() {
        return mesh;
    }

    public I3SNode setMesh(TriangleMesh mesh) {
        this.mesh = mesh;
        return this;
    }

    public int getFeatureCount() {
        return featureCount;
    }

    public I3SNode setFeatureCount(int featureCount) {
        this.featureCount = featureCount;
        return this;
    }

    public double getLodThreshold() {
        return lodThreshold;
    }

    public I3SNode setLodThreshold(double lodThreshold) {
        this.lodThreshold = lodThreshold;
        return this;
    }

    public int getOutputVertexCount() {
        return outputVertexCount >= 0 ? outputVertexCount : (mesh != null ? mesh.getTriangleCount() * 3 : 0);
    }

    public void setOutputVertexCount(int count) {
        this.outputVertexCount = count;
    }

    public void setTextureId(int textureId) {
        this.textureId = textureId;
    }

    public boolean hasTexture() {
        return textureId >= 0;
    }

    public void updateBoundingVolume() {
        if (mesh != null && !mesh.isEmpty()) {
            double[] bbox = mesh.computeBoundingBox();
            mbs = BoundingVolume.ofBoundingBox(bbox[0], bbox[1], bbox[2], bbox[3], bbox[4], bbox[5]);
        }

        for (I3SNode child : children) {
            if (child.mbs != null && mbs != null) {
                mbs = mbs.merge(child.mbs);
            } else if (child.mbs != null) {
                mbs = child.mbs;
            }
        }
    }
}
