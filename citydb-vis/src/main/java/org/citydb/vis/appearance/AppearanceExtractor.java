/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences (HFT Stuttgart) <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.appearance;

import org.citydb.model.appearance.Appearance;
import org.citydb.model.appearance.Color;
import org.citydb.model.appearance.GeoreferencedTexture;
import org.citydb.model.appearance.ParameterizedTexture;
import org.citydb.model.appearance.SurfaceData;
import org.citydb.model.appearance.SurfaceDataProperty;
import org.citydb.model.appearance.TextureCoordinate;
import org.citydb.model.appearance.X3DMaterial;
import org.citydb.model.common.ExternalFile;
import org.citydb.model.common.Matrix2x2;
import org.citydb.model.feature.Feature;
import org.citydb.model.geometry.Coordinate;
import org.citydb.model.geometry.LinearRing;
import org.citydb.model.geometry.Point;
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
 * Extracts CityGML appearance data — UV coordinates, texture image references
 * and X3DMaterial colors — from a {@link Feature}'s appearances on the caller
 * thread. Three appearance kinds are handled:
 * <ul>
 *   <li>{@link ParameterizedTexture}: uses pre-baked per-ring UV coordinates
 *       and a texture image; per-ring texture ids are tracked so each polygon
 *       surface ends up with its own texture in the atlas.</li>
 *   <li>{@link GeoreferencedTexture}: a single texture image draped over a
 *       region via a 2D affine transform (referencePoint + 2×2 orientation
 *       matrix). UVs are projected on the fly per ring vertex from world
 *       coordinates, then fed into the same texCoord/textureId pipeline as
 *       ParameterizedTexture.</li>
 *   <li>{@link X3DMaterial}: maps surface targets to a per-ring RGBA color
 *       (diffuseColor + (1 - transparency)).</li>
 * </ul>
 * <p>
 * Conflict resolution on the same ring:
 * <ul>
 *   <li><b>texture vs. material</b>: textures always win — material entries
 *       are dropped after dispatch (see the post-pass {@code removeAll}).</li>
 *   <li><b>ParameterizedTexture vs. GeoreferencedTexture</b>: PT always wins
 *       (authored UVs are more precise than a projection). Implemented by the
 *       asymmetric write semantics — PT uses {@code putAll} (overwrites) and
 *       GT uses {@code putIfAbsent} (does not overwrite), so PT wins
 *       regardless of dispatch order.</li>
 * </ul>
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
                } else if (sd instanceof GeoreferencedTexture gt && gt.hasTargets()) {
                    Point ref = gt.getReferencePoint().orElse(null);
                    Matrix2x2 orient = gt.getOrientation().orElse(null);
                    if (ref == null || orient == null) continue;
                    int gtTextureId = -1;
                    ExternalFile img = gt.getTextureImage().orElse(null);
                    if (img != null) {
                        gtTextureId = textureStore.register(img.getFileLocation());
                    }
                    double refX = ref.getCoordinate().getX();
                    double refY = ref.getCoordinate().getY();
                    double m00 = orient.get(0, 0);
                    double m01 = orient.get(0, 1);
                    double m10 = orient.get(1, 0);
                    double m11 = orient.get(1, 1);
                    for (Surface<?> target : gt.getTargets()) {
                        collectGeoreferencedRings(target, texCoordMap, ringTextureMap,
                                gtTextureId, refX, refY, m00, m01, m10, m11);
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

    /**
     * Walk a Surface and project per-vertex UVs onto every polygon ring
     * (exterior + interior) using the texture's affine 2D transform. Both
     * ring kinds get UV entries because {@code PolygonTriangulator}'s
     * hole-bridging logic looks up interior rings by identity to interpolate
     * UVs across the bridge edges. Only the exterior ring is recorded
     * against the textureId — that matches the per-polygon lookup pattern
     * used by the triangulator.
     * <p>
     * UV math (per CityGML spec): {@code [u, v] = orientation × (xy − refXY)}.
     * Coordinate axes are assumed consistent with the polygon vertices'
     * authored CRS — the {@code orientation} matrix already encodes any
     * world-axis-to-texture-axis mapping (e.g. V-axis flip).
     * <p>
     * UVs outside [0, 1] are passed through as-authored. The downstream
     * texture-atlas remapping treats the texture's atlas tile as the [0, 1]
     * region, so out-of-range UVs would sample neighbouring atlas content
     * rather than wrapping/clamping per the texture's wrap mode. For typical
     * GeoreferencedTexture use (one orthophoto draped onto a region whose
     * footprint is exactly the texture's coverage) UVs naturally land in
     * range, so this is a known but rare edge case.
     */
    private static void collectGeoreferencedRings(Surface<?> surface,
                                                  Map<LinearRing, List<TextureCoordinate>> uvSink,
                                                  Map<LinearRing, Integer> texSink,
                                                  int textureId,
                                                  double refX, double refY,
                                                  double m00, double m01,
                                                  double m10, double m11) {
        surface.accept(new ModelWalker() {
            @Override
            public void visit(Polygon polygon) {
                LinearRing ext = polygon.getExteriorRing();
                if (ext != null) {
                    uvSink.putIfAbsent(ext, projectRingUVs(ext, refX, refY, m00, m01, m10, m11));
                    if (textureId >= 0) {
                        texSink.putIfAbsent(ext, textureId);
                    }
                }
                if (polygon.hasInteriorRings()) {
                    for (LinearRing interior : polygon.getInteriorRings()) {
                        uvSink.putIfAbsent(interior,
                                projectRingUVs(interior, refX, refY, m00, m01, m10, m11));
                    }
                }
            }
        });
    }

    /**
     * Project a ring's points onto the texture plane via the affine 2D
     * transform. Generates one {@link TextureCoordinate} per
     * {@link LinearRing#getPoints()} entry (including the closing-coordinate
     * duplicate), matching the convention used by
     * {@code PolygonTriangulator.triangulatePolygon} when sizing UV arrays.
     */
    private static List<TextureCoordinate> projectRingUVs(LinearRing ring,
                                                          double refX, double refY,
                                                          double m00, double m01,
                                                          double m10, double m11) {
        List<Coordinate> points = ring.getPoints();
        List<TextureCoordinate> uvs = new ArrayList<>(points.size());
        for (Coordinate pt : points) {
            double dx = pt.getX() - refX;
            double dy = pt.getY() - refY;
            double u = m00 * dx + m01 * dy;
            double v = m10 * dx + m11 * dy;
            uvs.add(TextureCoordinate.of(u, v));
        }
        return uvs;
    }
}
