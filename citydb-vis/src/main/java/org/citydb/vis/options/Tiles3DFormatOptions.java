/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences (HFT Stuttgart) <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.options;

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
    // CESIUM_primitive_outline: per-primitive edge lists marking original
    // polygon boundary edges, drawn as outlines by CesiumJS. Default off —
    // the extension is Cesium-specific (other clients ignore it, geometry
    // still renders) and outline generation has load-time cost in the
    // viewer; --enable-outline opts in.
    private boolean enableOutline;
    // Suppress outlines on edges shared by two coplanar polygons of the same
    // feature and surface type — source data that subdivides one plane into
    // many polygons (TIN walls) otherwise renders as a wireframe. Null = off
    // (default; outlines mirror the source polygon layout). 0 = strictly
    // coplanar only (CAD-authored subdivisions); a positive value is the
    // maximum dihedral angle in degrees to treat as coplanar, for surveyed
    // data whose facet noise is continuous. Only takes effect together with
    // enableOutline.
    private Double outlineMergeCoplanar;

    public boolean isImplicitGeometryInstancing() {
        return implicitGeometryInstancing;
    }

    public Tiles3DFormatOptions setImplicitGeometryInstancing(boolean implicitGeometryInstancing) {
        this.implicitGeometryInstancing = implicitGeometryInstancing;
        return this;
    }

    public boolean isEnableOutline() {
        return enableOutline;
    }

    public Tiles3DFormatOptions setEnableOutline(boolean enableOutline) {
        this.enableOutline = enableOutline;
        return this;
    }

    public Double getOutlineMergeCoplanar() {
        return outlineMergeCoplanar;
    }

    public Tiles3DFormatOptions setOutlineMergeCoplanar(Double outlineMergeCoplanar) {
        this.outlineMergeCoplanar = outlineMergeCoplanar;
        return this;
    }
}
