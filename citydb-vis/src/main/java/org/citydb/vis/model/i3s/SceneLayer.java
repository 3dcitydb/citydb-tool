/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences (HFT Stuttgart) <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.model.i3s;

/**
 * Represents the I3S 3D Scene Layer descriptor (3dSceneLayer.json).
 * Describes the overall structure and capabilities of the I3S scene layer.
 */
public class SceneLayer {
    public static final String I3S_VERSION = "1.7";
    public static final String LAYER_TYPE = "3DObject";

    private String name;
    private String description;
    private int wkid;
    private double[] extent;

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

    public int getWkid() {
        return wkid;
    }

    public SceneLayer setWkid(int wkid) {
        this.wkid = wkid;
        return this;
    }

    public double[] getExtent() {
        return extent;
    }

    public SceneLayer setExtent(double[] extent) {
        this.extent = extent;
        return this;
    }
}
