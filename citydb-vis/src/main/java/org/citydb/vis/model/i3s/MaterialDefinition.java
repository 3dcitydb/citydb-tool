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

    private String cullFace;
    private PbrMetallicRoughness pbrMetallicRoughness;

    public static MaterialDefinition untextured() {
        MaterialDefinition material = new MaterialDefinition();
        material.cullFace = CULL_FACE;
        material.pbrMetallicRoughness = new PbrMetallicRoughness(null, 0);
        return material;
    }

    public static MaterialDefinition textured() {
        MaterialDefinition material = new MaterialDefinition();
        material.cullFace = CULL_FACE;
        material.pbrMetallicRoughness = new PbrMetallicRoughness(
                new BaseColorTexture(0, 0), 0);
        return material;
    }

    @JSONType(alphabetic = false)
    public record PbrMetallicRoughness(BaseColorTexture baseColorTexture, int metallicFactor) {
    }

    @JSONType(alphabetic = false)
    public record BaseColorTexture(int textureSetDefinitionId, int texCoord) {
    }
}
