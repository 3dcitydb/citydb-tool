/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences (HFT Stuttgart) <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.model.i3s;

import com.alibaba.fastjson2.annotation.JSONType;
import org.citydb.vis.styling.DefaultObjectStyle;

import java.util.List;

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

    /**
     * No-appearance material applied to features without texture or
     * X3DMaterial. {@code style.color()} drives {@code baseColorFactor}
     * (linear, glTF/I3S spec), emitted only when it differs from the
     * opaque-white default; an alpha &lt; 1 promotes to {@code BLEND}.
     * The paired {@link GeometryDefinition.DracoBuffer#untextured()} carries
     * NORMAL so the plain path renders shaded (PBR + Lambertian).
     */
    public static MaterialDefinition untextured(DefaultObjectStyle style) {
        MaterialDefinition material = pbr(null);
        if (style != null && style.hasNonDefaultColor()) {
            float[] linear = style.toLinearRgba();
            material.pbrMetallicRoughness = new PbrMetallicRoughness(null,
                    List.of(linear[0], linear[1], linear[2], linear[3]), 0);
            if (style.hasAlpha()) {
                material.alphaMode = ALPHA_MODE_BLEND;
            }
        }
        return material;
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
        material.pbrMetallicRoughness = new PbrMetallicRoughness(baseColor, null, 0);
        return material;
    }

    /**
     * I3S 1.7 PBR section. {@code baseColorFactor} is a linear-space
     * {@code [r, g, b, a]} multiplier per the I3S spec (mirrors glTF). Stored
     * as a {@code List<Float>} (rather than a fixed array) so fastjson2 can
     * omit it cleanly via the default null-skipping behaviour when the
     * caller leaves it at the spec default of {@code [1, 1, 1, 1]}.
     */
    @JSONType(alphabetic = false)
    public record PbrMetallicRoughness(BaseColorTexture baseColorTexture,
                                       List<Float> baseColorFactor,
                                       int metallicFactor) {
    }

    @JSONType(alphabetic = false)
    public record BaseColorTexture(int textureSetDefinitionId, int texCoord) {
    }
}
