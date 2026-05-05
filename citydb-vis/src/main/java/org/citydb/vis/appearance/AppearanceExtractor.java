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
import org.citydb.model.geometry.ImplicitGeometry;
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
 *       asymmetric write semantics — PT uses overwrite (via
 *       {@link #putNormalizedByPolygon}) and GT uses {@code putIfAbsent} (via
 *       {@link #normalizeAndWrite} with {@code overwrite=false}), so PT wins
 *       regardless of dispatch order.</li>
 * </ul>
 * <p>
 * UV normalization: both texture paths shift each polygon's UVs as a unit
 * by {@code (floor(minU), floor(minV))} so the values land near the origin
 * before reaching the texture atlas. CityGML's georeferenced UV
 * parameterization can produce ranges like {@code [-715, 715]} per texture;
 * without normalization the atlas would try to pre-bake the full tile grid
 * and the BSP packer's per-texture clamp would compress the source down to
 * sub-pixel-per-tile. Per-polygon (not per-ring) granularity keeps exterior
 * and interior rings of the same polygon in the same shifted UV space so
 * {@code PolygonTriangulator}'s hole-bridging interpolates correctly.
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
        return extractFrom(feature.getAppearances().getAll(), textureStore);
    }

    /**
     * Extract appearance data from a CityGML implicit geometry's prototype.
     * Implicit-geometry appearances live on the {@link ImplicitGeometry} (the
     * prototype), not on the parent feature, so this is a separate entry
     * point. The returned maps are keyed by the prototype's own
     * {@link LinearRing} identities — caller must remap to per-instance ring
     * identities when the prototype geometry is deep-copied for placement.
     */
    public static Result extract(ImplicitGeometry implicitGeometry, TextureStore textureStore) {
        if (!implicitGeometry.hasAppearances()) {
            return Result.empty();
        }
        return extractFrom(implicitGeometry.getAppearances().getAll(), textureStore);
    }

    private static Result extractFrom(Iterable<AppearanceProperty> appearances,
                                      TextureStore textureStore) {
        Map<LinearRing, List<TextureCoordinate>> texCoordMap = new IdentityHashMap<>();
        Map<LinearRing, Integer> ringTextureMap = new IdentityHashMap<>();
        Map<LinearRing, float[]> ringColorMap = new IdentityHashMap<>();

        for (AppearanceProperty ap : appearances) {
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
                    // Normalize per-polygon: shift each polygon's combined UV
                    // bounds by (floor(minU), floor(minV)) so the resulting UVs
                    // sit near the origin and the per-texture aggregate range
                    // reflects the largest single-polygon span instead of the
                    // global span across all uses of the texture. Atlas
                    // tiling allocates srcWidth × tilesU pixels, so an
                    // un-normalized georeferenced UV range like [-715, 715]
                    // would force the BSP packer to clamp the slot down to
                    // sub-pixel-per-tile.
                    putNormalizedByPolygon(texCoordMap, ptCoords);
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
     * Each polygon's projected UVs are normalized as a unit (see
     * {@link #putNormalizedByPolygon}) — exterior and interior rings of the
     * same polygon receive an identical shift so {@code PolygonTriangulator}'s
     * hole-bridging interpolates UVs in a consistent space.
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
                Map<LinearRing, List<TextureCoordinate>> projected = new IdentityHashMap<>();
                LinearRing ext = polygon.getExteriorRing();
                if (ext != null) {
                    projected.put(ext, projectRingUVs(ext, refX, refY, m00, m01, m10, m11));
                }
                if (polygon.hasInteriorRings()) {
                    for (LinearRing interior : polygon.getInteriorRings()) {
                        projected.put(interior,
                                projectRingUVs(interior, refX, refY, m00, m01, m10, m11));
                    }
                }
                // GT uses putIfAbsent semantics: ParameterizedTexture wins
                // when both target the same ring (asymmetric write rule).
                normalizeAndWrite(projected, uvSink, false);
                if (textureId >= 0 && ext != null) {
                    texSink.putIfAbsent(ext, textureId);
                }
            }
        });
    }

    /**
     * Normalize per-polygon: each polygon's combined ring UV bounds get a
     * single shift {@code (floor(minU), floor(minV))} applied uniformly to
     * all its rings so the result sits near {@code (0, 0)}. Without this,
     * georeferenced (world-coordinate) UV parameterizations like
     * {@code [-715, 715]} would force the texture atlas to allocate
     * {@code srcWidth × ceil(span)} pixels per texture and the BSP packer's
     * per-texture clamp to compress that down to sub-pixel-per-tile.
     * <p>
     * Polygons are identified by walking up from each ring via
     * {@link org.citydb.model.common.Child#getParent()} until a
     * {@link Polygon} is found. Rings whose parent is not a Polygon
     * (defensive) get an independent shift.
     * <p>
     * Source {@code TextureCoordinate} instances are not mutated — shifted
     * copies are produced — so the original CityGML model objects remain
     * unchanged for any other consumer (e.g., write-back paths).
     * <p>
     * Uses overwrite semantics, matching the original {@code putAll}: a later
     * ParameterizedTexture writing to a ring already populated by an earlier
     * one wins. The GeoreferencedTexture path uses
     * {@link #normalizeAndWrite} with {@code putIfAbsent} instead, so a PT
     * entry on the same ring is preserved (PT-wins-over-GT rule).
     */
    private static void putNormalizedByPolygon(
            Map<LinearRing, List<TextureCoordinate>> sink,
            Map<LinearRing, List<TextureCoordinate>> source) {
        normalizeAndWrite(source, sink, true);
    }

    /**
     * Group rings of {@code source} into shift-groups, compute a shared shift
     * per group, and write shifted copies into {@code sink}. {@code overwrite}
     * controls write semantics: {@code true} uses {@code put} (PT path);
     * {@code false} uses {@code putIfAbsent} (GT path) so a prior PT entry on
     * the same ring is preserved (PT-wins-over-GT rule).
     * <p>
     * Rings sharing the same parent {@link Polygon} go into one group.
     * Defensive: a ring whose ancestry chain doesn't reach a Polygon (should
     * not occur for well-formed CityGML) becomes its own singleton group, so
     * it still receives a normalized shift rather than being dropped.
     */
    private static void normalizeAndWrite(
            Map<LinearRing, List<TextureCoordinate>> source,
            Map<LinearRing, List<TextureCoordinate>> sink,
            boolean overwrite) {
        // Group key is the Polygon when present, otherwise the ring itself —
        // an Object key keeps the orphan and per-polygon paths uniform.
        Map<Object, List<LinearRing>> groups = new IdentityHashMap<>();
        for (LinearRing ring : source.keySet()) {
            Polygon parent = ring.getParent(Polygon.class);
            Object key = parent != null ? parent : ring;
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(ring);
        }

        for (List<LinearRing> rings : groups.values()) {
            float minU = Float.POSITIVE_INFINITY;
            float minV = Float.POSITIVE_INFINITY;
            for (LinearRing r : rings) {
                for (TextureCoordinate uv : source.get(r)) {
                    if (uv.getS() < minU) minU = uv.getS();
                    if (uv.getT() < minV) minV = uv.getT();
                }
            }
            // Empty UV lists across the whole group: leave shift at 0.
            float shiftU = Float.isFinite(minU) ? (float) Math.floor(minU) : 0f;
            float shiftV = Float.isFinite(minV) ? (float) Math.floor(minV) : 0f;
            for (LinearRing r : rings) {
                List<TextureCoordinate> shifted = shiftCopies(source.get(r), shiftU, shiftV);
                if (overwrite) {
                    sink.put(r, shifted);
                } else {
                    sink.putIfAbsent(r, shifted);
                }
            }
        }
    }

    /**
     * Build a fresh list of shifted {@link TextureCoordinate} instances. Always
     * allocates a new list so {@code sink} never aliases the model's internal
     * UV storage — keeps {@code Result.texCoords()}'s ownership contract clean
     * for any downstream stage that might mutate UVs (none today, but the cost
     * of one extra list allocation is negligible compared to the safety
     * margin).
     */
    private static List<TextureCoordinate> shiftCopies(List<TextureCoordinate> uvs,
                                                       float shiftU, float shiftV) {
        List<TextureCoordinate> out = new ArrayList<>(uvs.size());
        for (TextureCoordinate uv : uvs) {
            out.add(TextureCoordinate.of(uv.getS() - shiftU, uv.getT() - shiftV));
        }
        return out;
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
