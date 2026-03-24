/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.vis.scene;

/**
 * Represents the I3S 3D Scene Layer descriptor (3dSceneLayer.json).
 * Describes the overall structure and capabilities of the I3S scene layer.
 */
public class SceneLayer {
    public static final String I3S_VERSION = "1.7";
    public static final String LAYER_TYPE = "3DObject";

    private String name;
    private String description;
    private String spatialReference;
    private int wkid;
    private BoundingVolume fullExtent;
    private double[] extent;
    private int nodeCount;

    public String getName() {
        return name;
    }

    public SceneLayer setName(String name) {
        this.name = name;
        return this;
    }

    public String getDescription() {
        return description;
    }

    public SceneLayer setDescription(String description) {
        this.description = description;
        return this;
    }

    public String getSpatialReference() {
        return spatialReference;
    }

    public SceneLayer setSpatialReference(String spatialReference) {
        this.spatialReference = spatialReference;
        return this;
    }

    public int getWkid() {
        return wkid;
    }

    public SceneLayer setWkid(int wkid) {
        this.wkid = wkid;
        return this;
    }

    public BoundingVolume getFullExtent() {
        return fullExtent;
    }

    public SceneLayer setFullExtent(BoundingVolume fullExtent) {
        this.fullExtent = fullExtent;
        return this;
    }

    public double[] getExtent() {
        return extent;
    }

    public SceneLayer setExtent(double[] extent) {
        this.extent = extent;
        return this;
    }

    public int getNodeCount() {
        return nodeCount;
    }

    public SceneLayer setNodeCount(int nodeCount) {
        this.nodeCount = nodeCount;
        return this;
    }
}
