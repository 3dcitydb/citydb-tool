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
 * <p>
 * No {@code emissiveTexture} or {@code emissiveFactor} is emitted: CesiumJS
 * 1.139's I3S loader crashes on emissiveTexture, and a flat
 * {@code emissiveFactor} floor washed buildings out unacceptably in
 * testing. Brightness on back-facing walls under {@code --enable-shading}
 * is instead managed by the encoder's up-normal trick (see
 * {@link org.citydb.vis.encoder.i3s.I3SGeometryEncoder}).
 */
@JSONType(alphabetic = false)
public class MaterialDefinition {
    private static final String CULL_FACE = "none";
    // I3S 1.7 spec uses lowercase alpha-mode values ("opaque", "blend",
    // "mask"); CesiumJS uppercases internally before constructing the glTF
    // material (I3SGeometry.js: gltfMaterial.alphaMode.toUpperCase()), so
    // it accepts either case. ArcGIS Pro 3.x is strict and rejects the
    // uppercase glTF spelling — refuses to load the SLPK with a red error
    // — so we emit lowercase to match the I3S spec literal.
    private static final String ALPHA_MODE_BLEND = "blend";

    private String cullFace;
    private Boolean doubleSided;
    private String alphaMode;
    private PbrMetallicRoughness pbrMetallicRoughness;

    /**
     * No-appearance material applied to features without texture or
     * X3DMaterial. {@code style.color()} drives {@code baseColorFactor}
     * (linear, glTF/I3S spec), emitted only when it differs from the
     * opaque-white default; an alpha &lt; 1 promotes to {@code BLEND}.
     * Paired with {@link GeometryDefinition#untextured()} when
     * {@code --enable-shading} is on (PBR + Lambertian) and with
     * {@link GeometryDefinition#untexturedNoNormal()} otherwise (unlit).
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

    /**
     * Textured material backed by texture set 0. Lambertian darkening on
     * back-facing walls is mitigated on the encoder side via the up-normal
     * trick in {@link org.citydb.vis.encoder.i3s.I3SGeometryEncoder}
     * (truly-textured vertices share one ECEF up direction so walls and
     * roofs end up at the same brightness within a node).
     */
    public static MaterialDefinition textured() {
        return pbr(new BaseColorTexture(0, 0));
    }

    /**
     * Untextured material that consumes per-vertex COLOR_0. Paired with
     * {@link GeometryDefinition#colored()} when
     * {@code --enable-shading} is off (no NORMAL → unlit, authored
     * thematic / heat-map colours render at full intensity) or with
     * {@link GeometryDefinition#coloredShaded()} when on
     * (NORMAL present → PBR + Lambertian, authored colours pick up face
     * shading). {@code blend=true} emits {@code alphaMode=blend}; opaque
     * colored nodes rely on I3S's default opaque.
     */
    public static MaterialDefinition colored(boolean blend) {
        MaterialDefinition material = pbr(null);
        if (blend) {
            material.alphaMode = ALPHA_MODE_BLEND;
        }
        return material;
    }

    /**
     * Untextured material that consumes per-vertex COLOR_0 <i>and</i>
     * receives Lambertian shading from the paired
     * {@link GeometryDefinition#coloredShaded()} layout (which
     * carries NORMAL alongside COLOR). Used by the per-feature-type styling
     * pipeline so a uniform-coloured surface — e.g. all of a building's
     * RoofSurface triangles painted red via {@code --feature-type-style} —
     * still shows 3D form via per-face shading. Distinct from
     * {@link #colored(boolean)} (X3DMaterial) which is unlit by design.
     * <p>
     * {@code blend=true} emits {@code alphaMode=blend} for transparent
     * COLOR_0.a; Cesium decodes COLOR_0 as VEC4 FLOAT (alpha intact).
     * CesiumJS consumers must construct {@code I3SDataProvider} with
     * {@code adjustMaterialAlphaMode: true} — its default {@code false}
     * causes {@code I3SGeometry.js} to force-rewrite the declared
     * {@code alphaMode=blend} back to {@code OPAQUE}. Building Scene
     * Layers auto-enable the option; non-BSL layers need it set explicitly.
     * See {@link org.citydb.vis.encoder.i3s.I3SGeometryEncoder} javadoc.
     */
    public static MaterialDefinition coloredShaded(boolean blend) {
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
