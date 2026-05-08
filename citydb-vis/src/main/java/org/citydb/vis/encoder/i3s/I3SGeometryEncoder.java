/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.encoder.i3s;

import org.citydb.vis.geometry.TriangleMesh;
import org.citydb.vis.geometry.VertexWelder;
import org.citydb.vis.scene.BoundingVolume;
import org.citydb.vis.scene.SceneNode;
import org.citydb.vis.styling.DefaultObjectStyle;
import org.citydb.vis.styling.ObjectStyleRegistry;
import org.citydb.vis.util.BufferUtils;
import org.citydb.vis.util.GeoTransform;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Encodes I3S node meshes into the uncompressed legacy I3S 1.9binary
 * buffer at {@code nodes/N/geometries/0} — a single buffer per node,
 * consumed by both ArcGIS Pro / Online and CesiumJS. Attribute layout per
 * slot (NORMAL gated by {@code --enable-shading}):
 * <ul>
 *   <li>untextured (no appearance):      {@code position, [normal,] uv0, featureId, faceRange}</li>
 *   <li>textured:                        {@code position, [normal,] uv0, featureId, faceRange}</li>
 *   <li>textured-colored:                {@code position, [normal,] uv0, color, featureId, faceRange}</li>
 *   <li>colored (X3DMaterial):           {@code position, [normal,] uv0, color, featureId, faceRange}</li>
 *   <li>styled-colored (per-type style): {@code position, [normal,] uv0, color, featureId, faceRange}</li>
 * </ul>
 * NORMAL emission is gated by {@code --enable-shading}. ArcGIS Pro / Online
 * REQUIRE NORMAL — without it the per-vertex stream is mis-parsed and the
 * SLPK fails to load (red error indicator) — so any export targeted at
 * ArcGIS must pass the flag. CesiumJS works either way; with NORMAL it
 * shades, without it it auto-computes flat normals client-side
 * (controllable via {@code I3SDataProvider}'s {@code calculateNormals}
 * option). Position units: X/Y are degree-offsets from MBS center, Z is
 * meters — Cesium's {@code transformToLocal} hardcodes {@code scale_x=1}
 * for the binary path and applies {@code cartographic + toRadians(scale·position)}.
 * <p>
 * <b>Textured-slot up-normal trick:</b> truly-textured vertices in the
 * textured slot get the local ENU "up" direction (in ECEF) instead of
 * their real geometric normal. Lambertian then yields the same brightness
 * for every textured triangle in the node — walls and roofs are equally
 * lit, no per-face dimming on back-facing walls. White-pixel sentinel
 * triangles inside intra-feature-mixed nodes keep their real geometric
 * normal so they still pick up PBR shading. The 3D Tiles GLB writer
 * follows the same pattern (see
 * {@link org.citydb.vis.encoder.tiles3d.GlbEncoder}). On a mixed node
 * (X3DMaterial on some triangles, plain on others) the plain triangles
 * still get a colour baked into COLOR_0 — from the matching
 * {@code --feature-type-style} override if any, otherwise from
 * {@code --default-color}, otherwise white. On a pure-styling node
 * (no X3DMaterial) the styled-colored slot (COLOR_0, plus NORMAL when
 * shading is on) lets a uniform-colour surface — e.g. all of a building's
 * RoofSurface triangles painted red via
 * {@code --feature-type-style con:RoofSurface=#ff0000} — pick up
 * Lambertian shading. Pure-plain nodes with only {@code --default-color}
 * skip COLOR_0 entirely: the default colour rides on the untextured
 * material's {@code baseColorFactor}.
 * <p>
 * <b>Transparency on CesiumJS — consumer config required</b>: the
 * styled-colored slot encodes per-vertex COLOR_0.a and the paired
 * material declares {@code alphaMode=blend}. CesiumJS routes through the
 * standard glTF model pipeline, so the data renders transparency
 * correctly — but <i>only</i> when {@code I3SDataProvider} is constructed
 * with {@code adjustMaterialAlphaMode: true}. With the default
 * {@code false}, {@code I3SGeometry.js} actively force-rewrites any
 * {@code alphaMode=blend} on a non-{@code isTransparent}-flagged
 * primitive back to {@code OPAQUE}, dropping all transparency. Building
 * Scene Layers auto-enable the option; non-BSL layers (3DObject,
 * IntegratedMesh) need it set explicitly. The encoded I3S is
 * spec-compliant either way.
 */
public class I3SGeometryEncoder {
    /** Default RGBA for vertices in a layer that carries colors but a given
     *  mesh has none — keeps COLOR_0 in lock-step with the layer's
     *  buffer declaration without tinting the texture sample. */
    private static final float[] WHITE_RGBA = new float[]{1f, 1f, 1f, 1f};
    /** Padding UV for untextured vertices. Every slot declares {@code uv0}
     *  in its geometry buffer, so untextured vertices need a shared zero-UV
     *  reference to satisfy the per-vertex stream. The unlit/lit material
     *  on those slots ignores the sample, and gzip compresses the all-zero
     *  stream away. */
    private static final float[] ZERO_UV = new float[]{0f, 0f};

    // Per-export counters for --feature-type-style application coverage.
    // Why: I3S is one-material-per-node. The styled-colored slot fires only
    // on pure-plain nodes; nodes with texture or X3DMaterial silently fall
    // back (textured=fully dropped or partially baked on white-pixel
    // triangles; X3DMaterial=baked into colored slot but renders unlit).
    // The writer reads these after parallel encoding to surface a single
    // summary instead of per-node noise.
    private final AtomicInteger texturedNodesWithStyleConfig = new AtomicInteger();
    private final AtomicInteger x3dNodesWithStyleOverride = new AtomicInteger();

    /**
     * Result of encoding a node's geometry. {@code featureAabbs} is aligned
     * with {@code rangeFeatureIds} and contains the absolute-coordinate AABB
     * {@code [minX, minY, minZ, maxX, maxY, maxZ]} for each feature — the
     * ArcGIS Maps SDK for JavaScript needs distinct per-feature mbb values
     * to build the per-node pick BVH; a shared node-level bbox collapses
     * the BVH and makes picks intermittently miss under oblique views.
     */
    public record NodeGeometryResult(List<Long> rangeFeatureIds,
                                     List<double[]> featureAabbs) {
    }

    /**
     * Encode and write a node's geometry as the uncompressed legacy I3S 1.9
     * binary buffer at {@code nodes/N/geometries/0}.
     *
     * @return ordered feature ids and per-feature AABBs if a geometry file
     *         was written, {@code null} if welding/degenerate filtering left
     *         the mesh empty (caller should treat this node as a non-mesh
     *         node). The caller must use this list to align per-node
     *         feature/attribute output with the per-feature {@code featureId} /
     *         {@code faceRange} stream.
     */
    public NodeGeometryResult writeNodeGeometry(Path layerDir, SceneNode node,
                                                boolean layerHasColors,
                                                boolean enableShading,
                                                ObjectStyleRegistry styleRegistry) throws IOException {
        TriangleMesh mesh = node.getMesh();
        boolean hasTexCoords = mesh.hasTexCoords();
        boolean meshHasColors = mesh.hasColors();
        // Pure-X3DMaterial path: no texture + baked X3DMaterial vertex colors.
        // Textured nodes that also carry colors keep the textured material
        // and let glTF PBR modulate the texture sample via COLOR_0.
        boolean nodeIsX3DColored = !hasTexCoords && meshHasColors;

        if (!hasTexCoords) {
            node.setTextured(false);
        }

        BoundingVolume mbs = node.getBoundingVolume();
        double centerX = mbs.getCenterX();
        double centerY = mbs.getCenterY();
        double centerZ = mbs.getCenterZ();

        VertexWelder.WeldResult weld = VertexWelder.weldAndFilter(mesh, centerX, centerY, centerZ);
        node.setOutputVertexCount(weld.vertexCount());
        if (weld.isEmpty()) {
            node.setMesh(null);
            return null;
        }

        // Detect intra-feature mixed: textured node whose mesh still has
        // some triangles flagged untextured (texId < 0). The atlas builder
        // routes these via a reserved white-pixel sentinel region so they
        // ride through the textured material as flat white. Without
        // per-vertex bake the styling/default colour is dropped for those
        // triangles — the textured slot has no baseColorFactor.
        List<Integer> triTexIds = hasTexCoords ? mesh.getTriangleTextureIds() : null;
        boolean intraFeatureMixed = false;
        if (hasTexCoords) {
            for (int ti : weld.validTriIndices()) {
                if (triTexIds.get(ti) < 0) {
                    intraFeatureMixed = true;
                    break;
                }
            }
        }

        // Resolve per-triangle styles up front. Two independent flags drive
        // the downstream decisions:
        //  - anyTypeOverride: at least one triangle hit a per-type override
        //    (--feature-type-style). On a pure-plain node this switches
        //    routing from the untextured slot to the colored slot.
        //  - anyNonWhiteStyle: at least one triangle's resolved style
        //    produces a non-white colour. Decides whether per-vertex
        //    COLOR_0 baking is needed; the all-white case falls back to
        //    WHITE_RGBA in the iterator.
        // Entry conditions — the work is worth doing only when the registry
        // can produce a non-white colour somewhere AND the node has
        // somewhere to bake it:
        //  - Pure-untextured node with overrides, or with X3DMaterial +
        //    non-white default (mixed colored slot has no baseColorFactor,
        //    so the default colour can only land on per-vertex COLOR_0).
        //    Pure-plain nodes with only --default-color skip baking — the
        //    colour rides on the untextured material's baseColorFactor.
        //  - Textured node that contains intra-feature mixed triangles:
        //    bake styling/default colour onto the white-pixel-UV vertices
        //    so those polys aren't dropped to flat white.
        // Identity check `s != defaultStyle` works because
        // ObjectStyleRegistry's cache returns the same instance for every
        // type that resolves to the default style.
        DefaultObjectStyle[] perTriangleStyle = null;
        boolean anyTypeOverride = false;
        boolean anyNonWhiteStyle = false;
        boolean buildStyles = (!hasTexCoords && (styleRegistry.hasOverrides()
                || (meshHasColors && styleRegistry.defaultStyle().hasNonDefaultColor())))
                || (hasTexCoords && intraFeatureMixed && (styleRegistry.hasOverrides()
                        || styleRegistry.defaultStyle().hasNonDefaultColor()));
        if (buildStyles) {
            perTriangleStyle = resolveTriangleStyles(weld, mesh, styleRegistry);
            DefaultObjectStyle defaultStyle = styleRegistry.defaultStyle();
            for (DefaultObjectStyle s : perTriangleStyle) {
                if (s != defaultStyle) {
                    anyTypeOverride = true;
                }
                if (s.hasNonDefaultColor()) {
                    anyNonWhiteStyle = true;
                }
                if (anyTypeOverride && anyNonWhiteStyle) {
                    break;
                }
            }
            if (!anyNonWhiteStyle) {
                // Every triangle resolves to opaque white — no point
                // paying the per-vertex COLOR_0 cost; fall back to
                // WHITE_RGBA in the iterator.
                perTriangleStyle = null;
            }
        }
        // Styled-colored slot (NORMAL + COLOR_0 + alphaMode=blend) is used
        // only for pure-plain nodes that hit a per-type override. Mixed
        // nodes stay on the X3DMaterial colored slot 2/3 (one-material-per-
        // node I3S limit) and bake styled / default colours into COLOR_0
        // alongside the X3D-authored colours, but render unlit because
        // colored slot 2/3 has no NORMAL — X3DMaterial unlit policy.
        // Textured nodes that hit overrides on their white-pixel triangles
        // stay on the textured slot and bake into COLOR_0 there — the
        // texture sample on those vertices is white, so white × COLOR_0
        // = COLOR_0 yields the styling/default colour at full saturation.
        // Per-vertex alpha is encoded into COLOR_0.a; CesiumJS renders it
        // correctly when the consumer constructs I3SDataProvider with
        // adjustMaterialAlphaMode: true (see class javadoc).
        boolean useStyledColoredSlot = anyTypeOverride && !meshHasColors && !hasTexCoords;
        node.setColored(nodeIsX3DColored);
        node.setHasStyleOverride(useStyledColoredSlot);

        if (styleRegistry.hasOverrides()) {
            if (hasTexCoords) {
                texturedNodesWithStyleConfig.incrementAndGet();
            } else if (meshHasColors && anyTypeOverride) {
                x3dNodesWithStyleOverride.incrementAndGet();
            }
        }

        int vertexCount = weld.vertexCount();

        // COLOR must mirror the legacy buffer schema declared on the slot's
        // {@link GeometryDefinition.LegacyBuffer} (see
        // {@link SceneLayerDescriptor#buildGeometryDefinitions}). The schema
        // determines field presence in the JSON declaration; the encoder
        // here must emit (or omit) per-vertex bytes in lock-step or the
        // file size mismatches the layer's expectation. Emit when:
        //  - textured layer that also has colors (white padding),
        //  - X3DMaterial colored node (authored or padding),
        //  - styled-colored slot (styled colours baked).
        boolean emitColor = (layerHasColors && (hasTexCoords || nodeIsX3DColored))
                || useStyledColoredSlot;

        // NORMAL emission is gated by --enable-shading on every code path.
        // Without it the legacy buffer schema/binary omit normal and ArcGIS
        // Pro / Online refuse to load the scene layer (red error
        // indicator) — A/B-tested 2026-05-08. CesiumJS tolerates either
        // (it picks up shading when normal is present, renders unlit
        // otherwise). Anyone exporting for an ArcGIS target must pass
        // --enable-shading.
        boolean emitNormals = enableShading;
        float[][] outPositions = new float[vertexCount][];
        float[][] outNormals = emitNormals ? new float[vertexCount][3] : null;
        // outUVs is allocated for every node — every slot declares uv0 to
        // satisfy CesiumJS's _findBestGeometryBuffers(["position", "uv0"])
        // lookup. Untextured vertices share ZERO_UV.
        float[][] outUVs = new float[vertexCount][];
        float[][] outColors = emitColor ? new float[vertexCount][] : null;

        // ENU-to-ECEF rotation at the node's geographic center.
        // Note on normal consistency: per-polygon normals come from
        // PolygonTriangulator, which computes them in an ENU basis at the
        // polygon's own centroid latitude. Here we apply a single ENU-to-ECEF
        // rotation at the node's center latitude. For nodes spanning less than
        // a few kilometers the basis-rotation error between the two latitudes
        // is below the rendered-normal noise floor (<1e-4 rad). Larger nodes
        // would require per-triangle frame recomputation.
        GeoTransform.EnuBasis enu = outNormals != null
                ? GeoTransform.EnuBasis.at(centerX, centerY) : null;

        boolean[] anyAlphaBelowOne = new boolean[1];
        DefaultObjectStyle[] styles = perTriangleStyle;
        List<Integer> validTriIndices = weld.validTriIndices();
        List<Integer> triTexIdsFinal = triTexIds;
        VertexWelder.iterateOutputVertices(weld, mesh, (idx, weldedPos, srcIdx) -> {
            outPositions[idx] = weldedPos;
            if (outNormals != null) {
                int normalTriIdx = validTriIndices.get(idx / 3);
                boolean trulyTextured = hasTexCoords && triTexIdsFinal.get(normalTriIdx) >= 0;
                if (trulyTextured) {
                    enu.fillUpInEcef(outNormals[idx]);
                } else {
                    enu.rotateNormalToEcef(mesh.getNormals().get(srcIdx), outNormals[idx]);
                }
            }
            outUVs[idx] = hasTexCoords ? mesh.getTexCoords().get(srcIdx) : ZERO_UV;
            if (outColors != null) {
                int triLocalIdx = idx / 3;
                int srcTriIdx = validTriIndices.get(triLocalIdx);
                boolean whitePixelTri = hasTexCoords && triTexIdsFinal.get(srcTriIdx) < 0;
                boolean x3dColored = mesh.isTriangleColored(srcTriIdx);
                float[] c;
                if (whitePixelTri && !x3dColored && styles != null) {
                    // Intra-feature mixed: this triangle has no real texture
                    // (atlas builder pointed its UVs at the white-pixel
                    // sentinel). Bake the resolved styling / default colour
                    // into COLOR_0 — texture sample is white, so white ×
                    // COLOR_0 yields the chosen colour at full saturation.
                    // Skipped when the triangle is also X3D-authored: the
                    // next branch returns the authored RGBA, which wins
                    // over styling per X3DMaterial precedence.
                    c = styles[triLocalIdx].color();
                } else if (meshHasColors && (hasTexCoords || x3dColored)) {
                    // mesh.getColors() returns the authored X3DMaterial RGBA
                    // for X3D-authored vertices and WHITE_RGBA padding (set
                    // by mesh.merge()) for the rest. On textured nodes this
                    // passes through directly: textured-only polys carry
                    // white (no-op modulator on the texture sample), and
                    // X3D-authored polys tint their white-pixel fallback
                    // into the authored colour. On non-textured nodes only
                    // X3D-authored triangles enter this branch — non-X3D
                    // triangles fall through to styling or default white.
                    c = mesh.getColors().get(srcIdx);
                } else if (!hasTexCoords && styles != null) {
                    // Non-textured + non-X3D triangle on a styled node:
                    // bake the resolved style colour. Source is the
                    // matching --feature-type-style override when present,
                    // otherwise --default-color (the registry resolves
                    // both into one DefaultObjectStyle per triangle). Gate
                    // on !hasTexCoords so this fallback never tints a real
                    // textured triangle on a textured node — only the
                    // dedicated white-pixel branch above applies styling
                    // through a textured material.
                    c = styles[triLocalIdx].color();
                } else {
                    c = WHITE_RGBA;
                }
                outColors[idx] = c;
                if (c[3] < 0.999f) {
                    anyAlphaBelowOne[0] = true;
                }
            }
        });

        // Textured material is layer-level OPAQUE — mixed-node colored polys
        // lose alpha by design (one-material-per-node I3S limitation); only
        // pure-colored and styled-colored nodes can opt into BLEND.
        if (nodeIsX3DColored || useStyledColoredSlot) {
            node.setColoredBlend(anyAlphaBelowOne[0]);
        }

        List<double[]> featureAabbs = computeFeatureAabbs(weld, centerX, centerY, centerZ);

        node.setMesh(null);

        List<Long> rangeFeatureIds = weld.rangeFeatureIds();
        List<int[]> faceRanges = weld.faceRanges();

        Path geometryDir = layerDir.resolve("nodes").resolve(String.valueOf(node.getIndex()))
                .resolve("geometries");
        Files.createDirectories(geometryDir);

        // Position units: X/Y in DEGREES offset from MBS center, Z in METERS.
        // CesiumJS's binary path hardcodes scale_x=scale_y=1 and applies
        // `cartographicCenter + toRadians(scale * position)`; feeding meters
        // there teleports buildings into space. ArcGIS Pro reads the same
        // way. {@code outPositions} already holds the right values straight
        // out of {@link VertexWelder} — no transform needed.
        writeLegacyGeometry(geometryDir, outPositions, outNormals, outUVs, outColors,
                faceRanges, rangeFeatureIds);
        return new NodeGeometryResult(rangeFeatureIds, featureAabbs);
    }

    /**
     * Resolve each valid triangle's source surface type to a
     * {@link DefaultObjectStyle} via the registry. Returned array is
     * aligned with {@link VertexWelder.WeldResult#validTriIndices()} —
     * index {@code i / 3} on a per-vertex iteration locates the style for
     * the triangle owning vertex {@code i}.
     */
    private static DefaultObjectStyle[] resolveTriangleStyles(VertexWelder.WeldResult weld,
                                                              TriangleMesh mesh,
                                                              ObjectStyleRegistry registry) {
        List<Integer> validTriIndices = weld.validTriIndices();
        DefaultObjectStyle[] styles = new DefaultObjectStyle[validTriIndices.size()];
        for (int i = 0; i < validTriIndices.size(); i++) {
            styles[i] = registry.resolve(mesh.getTriangleSurfaceType(validTriIndices.get(i)));
        }
        return styles;
    }

    /**
     * Compute per-feature AABBs from welded (center-relative) positions,
     * converted back to absolute geographic coordinates. Returns a list
     * aligned with {@link VertexWelder.WeldResult#rangeFeatureIds()}.
     */
    private static List<double[]> computeFeatureAabbs(VertexWelder.WeldResult weld,
                                                       double centerX, double centerY,
                                                       double centerZ) {
        float[][] welded = weld.weldedPositions();
        List<Integer> validTri = weld.validTriIndices();
        List<int[]> faceRanges = weld.faceRanges();
        List<double[]> aabbs = new ArrayList<>(faceRanges.size());
        for (int[] range : faceRanges) {
            double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY,
                    minZ = Double.POSITIVE_INFINITY;
            double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY,
                    maxZ = Double.NEGATIVE_INFINITY;
            for (int i = range[0]; i <= range[1]; i++) {
                int base = validTri.get(i) * 3;
                for (int j = 0; j < 3; j++) {
                    float[] p = welded[base + j];
                    if (p[0] < minX) minX = p[0]; if (p[0] > maxX) maxX = p[0];
                    if (p[1] < minY) minY = p[1]; if (p[1] > maxY) maxY = p[1];
                    if (p[2] < minZ) minZ = p[2]; if (p[2] > maxZ) maxZ = p[2];
                }
            }
            aabbs.add(new double[]{
                    minX + centerX, minY + centerY, minZ + centerZ,
                    maxX + centerX, maxY + centerY, maxZ + centerZ});
        }
        return aabbs;
    }

    /**
     * Write the uncompressed legacy geometry buffer ({@code geometries/0}).
     * Binary layout per the I3S 1.9defaultGeometrySchema:
     * <pre>
     *   UInt32 LE  vertexCount, featureCount
     *   Float32 LE × 3 × vertexCount   positions  (X/Y deg-offset, Z meters)
     *   Float32 LE × 3 × vertexCount   normals    (when present)
     *   Float32 LE × 2 × vertexCount   uvs
     *   UInt8         × 4 × vertexCount colors    (when present)
     *   UInt64 LE     × featureCount   featureIds
     *   UInt32 LE × 2 × featureCount   faceRanges (start_face, end_face inclusive)
     * </pre>
     */
    private static void writeLegacyGeometry(Path geometryDir,
                                            float[][] positions, float[][] normals,
                                            float[][] uvs, float[][] colors,
                                            List<int[]> faceRanges,
                                            List<Long> rangeFeatureIds) throws IOException {
        int numVertices = positions.length;
        int numFeatures = rangeFeatureIds.size();

        int size = 8;
        size += numVertices * 3 * 4;
        if (normals != null) size += numVertices * 3 * 4;
        if (uvs != null) size += numVertices * 2 * 4;
        if (colors != null) size += numVertices * 4;
        size += numFeatures * 8;
        size += numFeatures * 8;

        ByteBuffer buf = BufferUtils.allocateLittleEndian(size);
        buf.putInt(numVertices);
        buf.putInt(numFeatures);

        for (float[] p : positions) {
            buf.putFloat(p[0]);
            buf.putFloat(p[1]);
            buf.putFloat(p[2]);
        }
        if (normals != null) {
            for (float[] n : normals) {
                buf.putFloat(n[0]);
                buf.putFloat(n[1]);
                buf.putFloat(n[2]);
            }
        }
        if (uvs != null) {
            for (float[] uv : uvs) {
                buf.putFloat(uv[0]);
                buf.putFloat(uv[1]);
            }
        }
        if (colors != null) {
            for (float[] c : colors) {
                buf.put((byte) Math.round(clampUnit(c[0]) * 255f));
                buf.put((byte) Math.round(clampUnit(c[1]) * 255f));
                buf.put((byte) Math.round(clampUnit(c[2]) * 255f));
                buf.put((byte) Math.round(clampUnit(c[3]) * 255f));
            }
        }
        for (long id : rangeFeatureIds) {
            buf.putLong(id);
        }
        for (int[] range : faceRanges) {
            buf.putInt(range[0]);
            buf.putInt(range[1]);
        }

        Files.write(geometryDir.resolve("0"), buf.array());
    }

    private static float clampUnit(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }

    public int getTexturedNodesWithStyleConfig() {
        return texturedNodesWithStyleConfig.get();
    }

    public int getX3DNodesWithStyleOverride() {
        return x3dNodesWithStyleOverride.get();
    }
}
