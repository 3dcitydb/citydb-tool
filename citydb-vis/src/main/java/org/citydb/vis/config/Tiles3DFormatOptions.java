/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences (HFT Stuttgart) <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.config;

import org.citydb.config.SerializableConfig;

/**
 * 3D Tiles 1.1 format options. Adds the 3D Tiles-only options on top of
 * {@link VisFormatOptions}; also serves as the configuration deserialization
 * hook ({@code @SerializableConfig}).
 */
@SerializableConfig(name = "3DTiles")
public class Tiles3DFormatOptions extends VisFormatOptions {
    // GPU instancing for implicit geometries (glTF EXT_mesh_gpu_instancing +
    // EXT_instance_features). Default off for consumers without support for
    // the two extensions; --implicit-geometry-instancing opts in to storing
    // each template mesh once per tile instead of baking a full mesh copy per
    // occurrence. Instances that cannot be expressed as prototype +
    // rotation·scale fall back to baking per instance regardless.
    private boolean implicitGeometryInstancing;

    public boolean isImplicitGeometryInstancing() {
        return implicitGeometryInstancing;
    }

    public Tiles3DFormatOptions setImplicitGeometryInstancing(boolean implicitGeometryInstancing) {
        this.implicitGeometryInstancing = implicitGeometryInstancing;
        return this;
    }
}
