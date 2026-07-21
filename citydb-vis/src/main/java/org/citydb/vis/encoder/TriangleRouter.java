/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.encoder;

import org.citydb.vis.geometry.TriangleMesh;
import org.citydb.vis.geometry.VertexWelder;
import org.citydb.vis.styling.DefaultObjectStyle;
import org.citydb.vis.styling.ObjectStyleRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Single source of truth for the format-neutral routing facts of a node's
 * valid triangles, shared by the I3S and 3D Tiles geometry encoders.
 * <p>
 * Both encoders must answer the same per-triangle questions before they can
 * emit geometry — "does it carry a texture?", "is it X3DMaterial-colored?",
 * "which {@link DefaultObjectStyle} does its surface type resolve to?", and
 * "which face range (property-table row) does it belong to?". Each encoder
 * used to compute these independently, which let the routing decision drift
 * between formats — a recurring source of mixed-texture / white-pixel bugs.
 * {@link #route} centralizes those facts; each encoder then applies its own
 * format policy (multi-primitive vs single buffer, atlas pages, color baking)
 * on top of an identical fact base.
 * <p>
 * The returned list is in triangle-soup order — index {@code i} corresponds to
 * {@link VertexWelder.WeldResult#validTriIndices()} position {@code i}, so a
 * per-vertex iteration at output vertex {@code idx} reads {@code route(...).get(idx / 3)}.
 */
public final class TriangleRouter {

    private TriangleRouter() {
    }

    /**
     * Format-neutral routing facts for one valid triangle.
     *
     * @param triangleIndex index into {@link TriangleMesh#getTriangles()}
     * @param faceIndex     face-range (property-table row) index this triangle
     *                      belongs to, aligned with
     *                      {@link VertexWelder.WeldResult#faceRanges()}
     * @param textureId     mesh texture id, or {@code -1} when the triangle
     *                      carries no texture reference; encoders map a
     *                      non-negative id to an atlas page themselves
     * @param colored       whether the triangle came from an X3DMaterial-colored
     *                      polygon ({@link TriangleMesh#isTriangleColored(int)})
     * @param style         the {@link DefaultObjectStyle} its source surface
     *                      type resolves to (never {@code null}; falls back to
     *                      the registry's default style)
     */
    public record RoutedTriangle(int triangleIndex, int faceIndex,
                                 int textureId, boolean colored,
                                 DefaultObjectStyle style) {
        /** Whether the triangle carries a real texture reference. */
        public boolean textured() {
            return textureId >= 0;
        }
    }

    /**
     * Routed triangles bucketed into the GLB primitive flavours: one bucket
     * per atlas page, plus the untextured colored (X3DMaterial vertex colors)
     * and plain leftovers, each in routed order.
     */
    public record PageBuckets(List<List<RoutedTriangle>> texturedByPage,
                              List<RoutedTriangle> colored,
                              List<RoutedTriangle> plain) {
    }

    /**
     * Bucket routed triangles by atlas page. A textured triangle goes to its
     * page's bucket; a texId missing from {@code texIdToPage} means the atlas
     * builder dropped the texture (e.g. corrupt source), so such triangles —
     * and every textured triangle when {@code texIdToPage} is {@code null}
     * (no atlas at all) — degrade to the colored/plain buckets so the shape
     * still renders.
     */
    public static PageBuckets bucketByPage(List<RoutedTriangle> routed, int pageCount,
                                           Map<Integer, Integer> texIdToPage) {
        List<List<RoutedTriangle>> texturedByPage = new ArrayList<>(pageCount);
        for (int i = 0; i < pageCount; i++) {
            texturedByPage.add(new ArrayList<>());
        }
        List<RoutedTriangle> colored = new ArrayList<>();
        List<RoutedTriangle> plain = new ArrayList<>();
        for (RoutedTriangle rt : routed) {
            if (rt.textured() && texIdToPage != null) {
                Integer page = texIdToPage.get(rt.textureId());
                if (page != null) {
                    texturedByPage.get(page).add(rt);
                    continue;
                }
            }
            if (rt.colored()) {
                colored.add(rt);
            } else {
                plain.add(rt);
            }
        }
        return new PageBuckets(texturedByPage, colored, plain);
    }

    /**
     * Classify every valid triangle of {@code weld} into its format-neutral
     * routing facts, walking face ranges once to assign each triangle its
     * property-table row.
     * <p>
     * Must be called only when {@code weld} is non-empty — the caller's
     * empty-mesh early-return guarantees {@code weld.faceRanges()} is non-empty.
     */
    public static List<RoutedTriangle> route(TriangleMesh mesh,
                                             VertexWelder.WeldResult weld,
                                             ObjectStyleRegistry styleRegistry) {
        List<Integer> validTriIndices = weld.validTriIndices();
        List<int[]> faceRanges = weld.faceRanges();
        // Texture ids are only meaningful when the mesh declares UVs; an
        // untextured mesh has no per-triangle texture lane, so treat every
        // triangle as texture-less.
        List<Integer> triTexIds = mesh.hasTexCoords() ? mesh.getTriangleTextureIds() : null;

        List<RoutedTriangle> routed = new ArrayList<>(validTriIndices.size());
        int rangeIdx = 0;
        int[] currentRange = faceRanges.get(0);
        for (int i = 0; i < validTriIndices.size(); i++) {
            while (i > currentRange[1]) {
                currentRange = faceRanges.get(++rangeIdx);
            }
            int ti = validTriIndices.get(i);
            int textureId = triTexIds != null ? triTexIds.get(ti) : -1;
            boolean colored = mesh.isTriangleColored(ti);
            DefaultObjectStyle style = styleRegistry.resolve(mesh.getTriangleSurfaceType(ti));
            routed.add(new RoutedTriangle(ti, rangeIdx, textureId, colored, style));
        }
        return routed;
    }
}
