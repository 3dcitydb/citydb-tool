/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences (HFT Stuttgart) <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.model.i3s;

import com.alibaba.fastjson2.annotation.JSONType;
import org.citydb.vis.styling.DefaultObjectStyle;

import java.util.List;

/**
 * Both {@code cullFace="none"} and {@code doubleSided=true} are emitted on
 * every material — different I3S clients read different fields:
 * <ul>
 *   <li><b>ArcGIS Pro</b> honours the legacy I3S 1.6-era {@code cullFace}
 *       enum. Required for Pro pick to find triangles whose CityGML winding
 *       points away from the camera (CityGML imports routinely have
 *       inconsistent wall winding).</li>
 *   <li><b>CesiumJS I3S loader</b> aligned with glTF in I3S 1.7+, reads the
 *       boolean {@code doubleSided}. Without this, Cesium back-face-culls
 *       wrong-winding triangles and they vanish from one viewing side.</li>
 * </ul>
 * Writing both is safe — clients ignore the field they don't recognise.
 */
@JSONType(alphabetic = false)
public class MaterialDefinition {
    private static final String CULL_FACE = "none";
    private static final String ALPHA_MODE_BLEND = "BLEND";

    private String cullFace;
    private Boolean doubleSided;
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
        material.doubleSided = true;
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
