/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences (HFT Stuttgart) <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.writer;

import org.citydb.model.appearance.Appearance;
import org.citydb.model.appearance.Color;
import org.citydb.model.appearance.ParameterizedTexture;
import org.citydb.model.appearance.SurfaceData;
import org.citydb.model.appearance.SurfaceDataProperty;
import org.citydb.model.appearance.TextureCoordinate;
import org.citydb.model.appearance.X3DMaterial;
import org.citydb.model.common.ExternalFile;
import org.citydb.model.feature.Feature;
import org.citydb.model.geometry.LinearRing;
import org.citydb.model.geometry.Polygon;
import org.citydb.model.geometry.Surface;
import org.citydb.model.property.AppearanceProperty;
import org.citydb.model.walker.ModelWalker;
import org.citydb.vis.store.TextureStore;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Extracts texture coordinates, texture image references and X3D material
 * colors from a {@link Feature}'s appearances on the caller thread. Each
 * {@link ParameterizedTexture} maps specific {@link LinearRing} instances to
 * UV coordinates and references a texture image; per-ring texture ids are
 * tracked so each polygon surface ends up with its own texture in the atlas.
 * Each {@link X3DMaterial} maps surface targets to a per-ring RGBA color
 * (diffuseColor + (1 - transparency)); textured rings take precedence over
 * material rings if both exist for the same ring.
 */
public final class AppearanceExtractor {
    /**
     * Per-feature appearance extraction result. All three maps are
     * format-neutral and meant to be consumed by any visualization writer:
     * the 3D Tiles GLB encoder reads {@code ringColors} into per-vertex
     * {@code COLOR_0}; the I3S encoder reads it into a Draco {@code COLOR}
     * attribute. Each map is {@code null} rather than empty when the feature
     * carries no data of that kind, so consumers can branch with a single
     * null check.
     * <p>
     * {@code ringColors} RGBA values are kept in their authored
     * <strong>sRGB display space</strong> — the two writers handle gamma
     * differently, so the conversion is left to each consumer:
     * <ul>
     *   <li>The 3D Tiles GLB encoder applies sRGB→linear before baking into
     *       {@code COLOR_0}, since glTF mandates linear color space and
     *       CesiumJS does linear→sRGB on output.</li>
     *   <li>The I3S encoder writes the raw sRGB values straight into the
     *       Draco {@code COLOR} attribute, since CesiumJS's I3S loader
     *       treats {@code COLOR_0} as already-sRGB and skips that
     *       conversion.</li>
     * </ul>
     * Alpha is treated as a numeric scalar (not a color channel) and is
     * left as authored regardless.
     */
    public record Result(Map<LinearRing, List<TextureCoordinate>> texCoords,
                         Map<LinearRing, Integer> ringTextureIds,
                         Map<LinearRing, float[]> ringColors) {
        public boolean isEmpty() {
            return texCoords == null && ringColors == null;
        }

        public static Result empty() {
            return new Result(null, null, null);
        }
    }

    private AppearanceExtractor() {
    }

    public static Result extract(Feature feature, TextureStore textureStore) {
        if (!feature.hasAppearances()) {
            return Result.empty();
        }

        Map<LinearRing, List<TextureCoordinate>> texCoordMap = new IdentityHashMap<>();
        Map<LinearRing, Integer> ringTextureMap = new IdentityHashMap<>();
        Map<LinearRing, float[]> ringColorMap = new IdentityHashMap<>();

        for (AppearanceProperty ap : feature.getAppearances().getAll()) {
            Appearance appearance = ap.getObject();
            if (appearance == null) continue;
            for (SurfaceDataProperty sdp : appearance.getSurfaceData()) {
                SurfaceData<?> sd = sdp.getObject().orElse(null);
                if (sd instanceof ParameterizedTexture pt && pt.hasTextureCoordinates()) {
                    int ptTextureId = -1;
                    ExternalFile img = pt.getTextureImage().orElse(null);
                    if (img != null) {
                        ptTextureId = textureStore.register(img.getFileLocation());
                    }
                    Map<LinearRing, List<TextureCoordinate>> ptCoords =
                            pt.getTextureCoordinates();
                    texCoordMap.putAll(ptCoords);
                    if (ptTextureId >= 0) {
                        for (LinearRing ring : ptCoords.keySet()) {
                            ringTextureMap.put(ring, ptTextureId);
                        }
                    }
                } else if (sd instanceof X3DMaterial xm && xm.hasTargets()) {
                    float[] rgba = toRgba(xm);
                    if (rgba == null) continue;
                    for (Surface<?> target : xm.getTargets()) {
                        collectExteriorRings(target, ringColorMap, rgba);
                    }
                }
            }
        }

        // Texture wins over material on the same ring: drop material entries
        // for rings that already received a texture id.
        if (!ringColorMap.isEmpty() && !ringTextureMap.isEmpty()) {
            ringColorMap.keySet().removeAll(ringTextureMap.keySet());
        }

        boolean noTextures = texCoordMap.isEmpty();
        boolean noColors = ringColorMap.isEmpty();
        if (noTextures && noColors) {
            return Result.empty();
        }
        return new Result(
                noTextures ? null : texCoordMap,
                noTextures ? null : ringTextureMap,
                noColors ? null : ringColorMap);
    }

    private static float[] toRgba(X3DMaterial material) {
        Color diffuse = material.getDiffuseColor().orElse(null);
        if (diffuse == null) {
            return null;
        }
        // X3D material's transparency: 0 = opaque, 1 = fully transparent.
        // glTF/CesiumJS use alpha (1 = opaque), so flip.
        double alpha = 1.0 - material.getTransparency().orElse(0.0);
        // CityGML/X3D diffuseColor is authored as sRGB display values; we
        // pass them through as-is so each writer can apply the gamma
        // treatment its consumer expects (see {@link Result} javadoc).
        return new float[]{
                (float) diffuse.getRed(),
                (float) diffuse.getGreen(),
                (float) diffuse.getBlue(),
                (float) alpha};
    }

    /**
     * Walk a Surface and record all its polygons' exterior rings against the
     * given color. {@link org.citydb.vis.geometry.PolygonTriangulator} keys
     * its per-polygon color lookup by exterior ring identity, so only those
     * need entries here — interior rings inherit the polygon's color via the
     * triangulator's per-polygon scope.
     */
    private static void collectExteriorRings(Surface<?> surface,
                                             Map<LinearRing, float[]> sink,
                                             float[] rgba) {
        List<LinearRing> exteriors = new ArrayList<>();
        surface.accept(new ModelWalker() {
            @Override
            public void visit(Polygon polygon) {
                LinearRing ext = polygon.getExteriorRing();
                if (ext != null) {
                    exteriors.add(ext);
                }
            }
        });
        for (LinearRing ring : exteriors) {
            sink.putIfAbsent(ring, rgba);
        }
    }
}
