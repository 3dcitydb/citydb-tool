/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.appearance;

import org.citydb.model.common.Name;
import org.citydb.vis.geometry.TriangleMesh;
import org.citydb.vis.store.TextureStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression tests for the {@code compositeAtlas} / {@code computeUvRegions}
 * extraction. They drive the public {@link TextureAtlasBuilder#build} and
 * {@link TextureAtlasBuilder#buildMulti} entry points with real on-disk
 * textures so both refactored helpers run, and pin the white-pixel behaviour:
 * the sentinel is excluded from {@link TextureAtlas#getTextureIds()} on the
 * single-page path and never surfaces on the multi-page ({@code composePage})
 * path.
 */
class TextureAtlasTest {

    @TempDir
    Path tempDir;

    private TextureStore store;

    private TextureStore newStore() {
        return new TextureStore(tempDir);
    }

    /** Write a solid-colour PNG into the temp dir and register it. */
    private int registerTexture(String name, int w, int h, Color color) throws IOException {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = img.createGraphics();
        g.setColor(color);
        g.fillRect(0, 0, w, h);
        g.dispose();
        ImageIO.write(img, "png", tempDir.resolve(name).toFile());
        return store.register(name);
    }

    @Test
    void buildSinglePagePacksAllTexturesWithoutSentinel() throws IOException {
        store = newStore();
        int t0 = registerTexture("a.png", 8, 8, Color.RED);
        int t1 = registerTexture("b.png", 16, 16, Color.GREEN);
        int t2 = registerTexture("c.png", 8, 16, Color.BLUE);
        List<Integer> ids = List.of(t0, t1, t2);

        TextureAtlas atlas = TextureAtlasBuilder.build(ids, store, 1.0, 1024, null,
                false, AtlasFallbackStrategy.RESCALE);

        assertNotNull(atlas);
        assertEquals(new HashSet<>(ids), atlas.getTextureIds());
        assertTrue(atlas.getWidth() > 0 && atlas.getHeight() > 0);
        // No overflow at this size -> requested scale is honoured untouched.
        assertEquals(1.0, atlas.getActualScale(), 1e-9);
    }

    @Test
    void buildWithWhitePixelExcludesSentinelAndRemapsUntexturedVertices() throws IOException {
        store = newStore();
        int t0 = registerTexture("a.png", 16, 16, Color.RED);

        TextureAtlas atlas = TextureAtlasBuilder.build(List.of(t0), store, 1.0, 1024, null,
                true, AtlasFallbackStrategy.RESCALE);
        assertNotNull(atlas);

        // The reserved white-pixel sentinel must never appear as a real texture.
        assertFalse(atlas.getTextureIds().contains(TextureAtlas.WHITE_PIXEL_TEX_ID));
        assertTrue(atlas.getTextureIds().contains(t0));

        // Mixed node: one textured triangle (t0) + one untextured triangle (-1).
        TriangleMesh mesh = new TriangleMesh();
        int u0 = mesh.addVertex(0, 0, 0, 0, 0, 1, 0f, 0f);
        int u1 = mesh.addVertex(1, 0, 0, 0, 0, 1, 1f, 0f);
        int u2 = mesh.addVertex(0, 1, 0, 0, 0, 1, 0f, 1f);
        mesh.addTriangle(u0, u1, u2, 1L, t0, false, Name.of("WallSurface"));
        int n0 = mesh.addVertex(5, 5, 0, 0, 0, 1);
        int n1 = mesh.addVertex(6, 5, 0, 0, 0, 1);
        int n2 = mesh.addVertex(5, 6, 0, 0, 0, 1);
        mesh.addTriangle(n0, n1, n2, 2L, -1, false, Name.of("RoofSurface"));

        atlas.remapUVs(mesh);

        // Untextured-only vertices were redirected to the single white-pixel
        // sample point: identical for all, and a valid UV inside [0,1].
        List<float[]> uv = mesh.getTexCoords();
        float[] w0 = uv.get(n0), w1 = uv.get(n1), w2 = uv.get(n2);
        assertEquals(w0[0], w1[0], 1e-9);
        assertEquals(w0[1], w1[1], 1e-9);
        assertEquals(w0[0], w2[0], 1e-9);
        assertEquals(w0[1], w2[1], 1e-9);
        assertTrue(w0[0] >= 0f && w0[0] <= 1f && w0[1] >= 0f && w0[1] <= 1f);
    }

    // ---- remapUVs: region / rotation / tiling transforms --------------------
    //
    // remapUVs is the last thing that touches a UV before it is written into a
    // GLB or an I3S geometry buffer, and all three of its branches fail
    // silently: a wrong region offset puts a building's texture on its
    // neighbour, a wrong rotation renders it sideways, a wrong tile
    // normalization stretches it. The tests below drive the branches through
    // the layout data the builder produces rather than through the packer, so
    // the hand-derived formulas are pinned independently of which layout the
    // BSP packer happens to choose for a given fixture.

    private static final Name WALL = Name.of("WallSurface");
    private static final float UV_EPS = 1e-6f;
    /** Source UVs of a quad covering the whole texture, CCW from (0,0). */
    private static final float[][] UNIT_QUAD_UVS = {{0f, 0f}, {1f, 0f}, {1f, 1f}, {0f, 1f}};

    /**
     * A textured quad: four vertices carrying the given source-space UVs (CCW
     * from the lower-left corner) and two triangles over them, so the mesh's
     * texCoords list is index-aligned with {@code uvCorners}.
     */
    private static TriangleMesh texturedQuad(int texId, float[][] uvCorners) {
        TriangleMesh mesh = new TriangleMesh();
        appendTexturedQuad(mesh, texId, uvCorners);
        return mesh;
    }

    private static void appendTexturedQuad(TriangleMesh mesh, int texId, float[][] uvCorners) {
        // Quads are placed far apart so welding can never merge them.
        double x = mesh.getVertexCount() * 10.0;
        int v0 = mesh.addVertex(x, 0, 0, 0, 0, 1, uvCorners[0][0], uvCorners[0][1]);
        int v1 = mesh.addVertex(x + 1, 0, 0, 0, 0, 1, uvCorners[1][0], uvCorners[1][1]);
        int v2 = mesh.addVertex(x + 1, 1, 0, 0, 0, 1, uvCorners[2][0], uvCorners[2][1]);
        int v3 = mesh.addVertex(x, 1, 0, 0, 0, 1, uvCorners[3][0], uvCorners[3][1]);
        mesh.addTriangle(v0, v1, v2, 1L, texId, false, WALL);
        mesh.addTriangle(v0, v2, v3, 1L, texId, false, WALL);
    }

    /**
     * An atlas page carrying only the layout {@code remapUVs} reads: the
     * texture's normalized region, its tile mapping
     * {@code [offsetU, offsetV, rangeU, rangeV]} and whether the packer rotated
     * it. No white pixel is reserved — that branch is covered above.
     */
    private static TextureAtlas layoutOnlyAtlas(int texId, float[] region, float[] tile,
                                                boolean rotated) {
        return new TextureAtlas(
                Map.of(texId, region),
                new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB),
                Map.of(texId, tile),
                rotated ? Set.of(texId) : Set.of(),
                null,
                1.0);
    }

    private static void assertUv(float[] expected, float[] actual, String corner) {
        assertEquals(expected[0], actual[0], UV_EPS, corner + ".u");
        assertEquals(expected[1], actual[1], UV_EPS, corner + ".v");
    }

    @Test
    void untiledUvsAreScaledAndOffsetIntoTheTexturesAtlasRegion() {
        TriangleMesh mesh = texturedQuad(7, UNIT_QUAD_UVS);

        layoutOnlyAtlas(7, new float[]{0.25f, 0.5f, 0.25f, 0.5f},
                new float[]{0f, 0f, 1f, 1f}, false).remapUVs(mesh);

        // [0,1]² maps onto the region rectangle, corner for corner.
        List<float[]> uv = mesh.getTexCoords();
        assertUv(new float[]{0.25f, 0.5f}, uv.get(0), "(0,0)");
        assertUv(new float[]{0.5f, 0.5f}, uv.get(1), "(1,0)");
        assertUv(new float[]{0.5f, 1.0f}, uv.get(2), "(1,1)");
        assertUv(new float[]{0.25f, 1.0f}, uv.get(3), "(0,1)");
    }

    @Test
    void rotatedTextureTurnsUvsNinetyDegreesCcwWithoutMirroring() {
        TriangleMesh mesh = texturedQuad(7, UNIT_QUAD_UVS);

        layoutOnlyAtlas(7, new float[]{0.25f, 0.5f, 0.25f, 0.5f},
                new float[]{0f, 0f, 1f, 1f}, true).remapUVs(mesh);

        // The packer stored the source rotated 90° CCW, so the source's +u axis
        // must come back out as the region's -v axis and +v as +u.
        List<float[]> uv = mesh.getTexCoords();
        assertUv(new float[]{0.25f, 1.0f}, uv.get(0), "(0,0)");
        assertUv(new float[]{0.25f, 0.5f}, uv.get(1), "(1,0)");
        assertUv(new float[]{0.5f, 0.5f}, uv.get(2), "(1,1)");
        assertUv(new float[]{0.5f, 1.0f}, uv.get(3), "(0,1)");

        // A transpose (u,v)->(v,u) maps the same four corners onto the same four
        // region corners, just assigned differently — so corner membership alone
        // cannot tell a rotation from a mirror. Winding can, and it is the
        // property that survives any relabeling of the quad: the source quad is
        // CCW, and a mirrored blit would come back CW.
        assertTrue(signedUvArea(uv) > 0, "a rotation must preserve UV winding, a mirror flips it");
    }

    @Test
    void tiledUvsAreNormalizedOverTheRepeatedSpanIncludingNegativeOffsets() {
        // Source UVs span u in [-2, 1] and v in [0, 2], i.e. three horizontal
        // and two vertical repeats, which the builder records as
        // tile = [floor(minU), floor(minV), tilesU, tilesV] = [-2, 0, 3, 2].
        // The negative floor is where an off-by-one lands, and CityGML data
        // routinely authors negative UVs.
        TriangleMesh mesh = texturedQuad(7, new float[][]{
                {-2f, 0f}, {1f, 0f}, {1f, 2f}, {-2f, 2f}});

        layoutOnlyAtlas(7, new float[]{0f, 0f, 0.5f, 0.5f},
                new float[]{-2f, 0f, 3f, 2f}, false).remapUVs(mesh);

        // The whole repeated span — not the [0,1] unit square — is what maps
        // onto the region, because the atlas holds the repeats as real pixels.
        List<float[]> uv = mesh.getTexCoords();
        assertUv(new float[]{0f, 0f}, uv.get(0), "span origin");
        assertUv(new float[]{0.5f, 0f}, uv.get(1), "u span end");
        assertUv(new float[]{0.5f, 0.5f}, uv.get(2), "u+v span end");
        assertUv(new float[]{0f, 0.5f}, uv.get(3), "v span end");
    }

    @Test
    void rotatedAndTiledUsesTheSwappedTileParametersOnEachAxis() {
        // u in [1, 3] (offset 1, two tiles), v in [2, 5] (offset 2, three
        // tiles): both offsets are non-zero and the two ranges differ, so every
        // term of the rotated branch carries weight — the atlas u axis must
        // consume (offsetV, rangeV) and the atlas v axis (offsetU, rangeU).
        // Feeding it the unswapped pair leaves the corners off by a tile.
        TriangleMesh mesh = texturedQuad(7, new float[][]{
                {1f, 2f}, {3f, 2f}, {3f, 5f}, {1f, 5f}});

        layoutOnlyAtlas(7, new float[]{0.1f, 0.2f, 0.4f, 0.6f},
                new float[]{1f, 2f, 2f, 3f}, true).remapUVs(mesh);

        List<float[]> uv = mesh.getTexCoords();
        assertUv(new float[]{0.1f, 0.8f}, uv.get(0), "(minU,minV)");
        assertUv(new float[]{0.1f, 0.2f}, uv.get(1), "(maxU,minV)");
        assertUv(new float[]{0.5f, 0.2f}, uv.get(2), "(maxU,maxV)");
        assertUv(new float[]{0.5f, 0.8f}, uv.get(3), "(minU,maxV)");
    }

    @Test
    void remappedUvsOfDifferentTexturesLandInDisjointRegionsOfThePage() throws IOException {
        store = newStore();
        int t0 = registerTexture("a.png", 64, 64, Color.RED);
        int t1 = registerTexture("b.png", 64, 64, Color.GREEN);

        TextureAtlas atlas = TextureAtlasBuilder.build(List.of(t0, t1), store, 1.0, 1024,
                null, false, AtlasFallbackStrategy.RESCALE);
        assertNotNull(atlas);

        // One full-range quad per texture on the same page: vertices 0..3 are
        // t0's, 4..7 are t1's.
        TriangleMesh mesh = texturedQuad(t0, UNIT_QUAD_UVS);
        appendTexturedQuad(mesh, t1, UNIT_QUAD_UVS);

        atlas.remapUVs(mesh);

        float[] first = uvBounds(mesh, 0, 4);
        float[] second = uvBounds(mesh, 4, 8);
        for (float[] bounds : List.of(first, second)) {
            assertTrue(bounds[0] >= -UV_EPS && bounds[1] >= -UV_EPS
                            && bounds[2] <= 1f + UV_EPS && bounds[3] <= 1f + UV_EPS,
                    "remapped UVs must stay inside the page: " + Arrays.toString(bounds));
        }
        // Whatever layout the packer chose — side by side, stacked, either one
        // rotated — the two textures own disjoint sub-rectangles. Overlapping
        // bounds mean a quad kept its source-space UVs or both were mapped onto
        // one region, which renders as the wrong texture on half the mesh.
        // Touching edges are fine, hence the epsilon on each side.
        assertTrue(first[2] <= second[0] + UV_EPS || second[2] <= first[0] + UV_EPS
                        || first[3] <= second[1] + UV_EPS || second[3] <= first[1] + UV_EPS,
                "the two textures' UV bounds overlap: " + Arrays.toString(first)
                        + " vs " + Arrays.toString(second));
    }

    /** UV bounds {@code [minU, minV, maxU, maxV]} over vertices {@code [from, to)}. */
    private static float[] uvBounds(TriangleMesh mesh, int from, int to) {
        float[] bounds = {Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        for (int v = from; v < to; v++) {
            float[] uv = mesh.getTexCoords().get(v);
            bounds[0] = Math.min(bounds[0], uv[0]);
            bounds[1] = Math.min(bounds[1], uv[1]);
            bounds[2] = Math.max(bounds[2], uv[0]);
            bounds[3] = Math.max(bounds[3], uv[1]);
        }
        return bounds;
    }

    /** Shoelace area over the first four (quad) UVs, signed by winding. */
    private static double signedUvArea(List<float[]> uv) {
        double area = 0;
        for (int i = 0; i < 4; i++) {
            float[] p = uv.get(i), q = uv.get((i + 1) % 4);
            area += p[0] * q[1] - q[0] * p[1];
        }
        return area / 2;
    }

    @Test
    void buildMultiCoversAllTexturesAcrossPages() throws IOException {
        store = newStore();
        // Three 200x200 textures cannot share one 256x256 page at scale 1.0,
        // so the BSP packer must spill onto additional levels -> >= 2 pages.
        int t0 = registerTexture("a.png", 200, 200, Color.RED);
        int t1 = registerTexture("b.png", 200, 200, Color.GREEN);
        int t2 = registerTexture("c.png", 200, 200, Color.BLUE);
        List<Integer> ids = List.of(t0, t1, t2);

        List<TextureAtlas> pages = TextureAtlasBuilder.buildMulti(ids, store, 1.0, 256, null);

        assertTrue(pages.size() >= 2, "the 256 cap must force a genuine multi-page spill");
        Set<Integer> covered = new HashSet<>();
        for (TextureAtlas page : pages) {
            assertNotNull(page);
            assertTrue(page.getWidth() > 0 && page.getHeight() > 0);
            // composePage never reserves a white pixel — on any page.
            assertFalse(page.getTextureIds().contains(TextureAtlas.WHITE_PIXEL_TEX_ID));
            covered.addAll(page.getTextureIds());
        }
        assertEquals(new HashSet<>(ids), covered);
    }
}
