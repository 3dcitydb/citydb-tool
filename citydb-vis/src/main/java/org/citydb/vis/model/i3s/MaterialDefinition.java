/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences (HFT Stuttgart) <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.model.i3s;

import com.alibaba.fastjson2.annotation.JSONType;

/**
 * {@code cullFace = "none"} rather than "back": CityGML imports often mix
 * triangle winding on walls, and "back" culling then removes those
 * triangles from the ArcGIS pick buffer, making the feature unpickable
 * from certain camera angles even though it still renders correctly on
 * the lit side.
 */
@JSONType(alphabetic = false)
public class MaterialDefinition {
    private static final String CULL_FACE = "none";
    private static final String ALPHA_MODE_BLEND = "BLEND";

    private String cullFace;
    private String alphaMode;
    private PbrMetallicRoughness pbrMetallicRoughness;

    public static MaterialDefinition untextured() {
        return pbr(null);
    }

    public static MaterialDefinition textured() {
        return pbr(new BaseColorTexture(0, 0));
    }

    /**
     * Untextured material that consumes per-vertex COLOR_0. Renders unlit
     * (no Lambertian shading) because the paired Draco buffer
     * ({@link GeometryDefinition.DracoBuffer#colored()}) omits NORMAL — the
     * CesiumJS I3S loader skips lighting when NORMAL is absent. This is the
     * I3S equivalent of the 3D Tiles colored material's
     * {@code KHR_materials_unlit}; both paths must stay unlit so authored
     * thematic / heat-map colors render at full intensity. {@code blend=true}
     * emits {@code alphaMode=BLEND} for X3DMaterial transparency; opaque
     * colored nodes rely on I3S's default OPAQUE.
     */
    public static MaterialDefinition colored(boolean blend) {
        MaterialDefinition material = pbr(null);
        if (blend) {
            material.alphaMode = ALPHA_MODE_BLEND;
        }
        return material;
    }

    private static MaterialDefinition pbr(BaseColorTexture baseColor) {
        MaterialDefinition material = new MaterialDefinition();
        material.cullFace = CULL_FACE;
        material.pbrMetallicRoughness = new PbrMetallicRoughness(baseColor, 0);
        return material;
    }

    @JSONType(alphabetic = false)
    public record PbrMetallicRoughness(BaseColorTexture baseColorTexture, int metallicFactor) {
    }

    @JSONType(alphabetic = false)
    public record BaseColorTexture(int textureSetDefinitionId, int texCoord) {
    }
}
