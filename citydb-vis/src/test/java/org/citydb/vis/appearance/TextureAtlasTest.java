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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression tests for the {@code compositeAtlas} / {@code computeUvRegions}
 * extraction. They drive the public {@code build} and {@code buildMulti}
 * entry points with real on-disk textures so both refactored helpers run, and
 * pin the white-pixel behaviour: the sentinel is excluded from
 * {@link TextureAtlas#getTextureIds()} on the single-page path and never
 * surfaces on the multi-page ({@code composePage}) path.
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

        TextureAtlas atlas = TextureAtlas.build(ids, store, 1.0, 1024, null,
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

        TextureAtlas atlas = TextureAtlas.build(List.of(t0), store, 1.0, 1024, null,
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

    @Test
    void buildMultiCoversAllTexturesAcrossPages() throws IOException {
        store = newStore();
        int t0 = registerTexture("a.png", 8, 8, Color.RED);
        int t1 = registerTexture("b.png", 16, 16, Color.GREEN);
        int t2 = registerTexture("c.png", 32, 8, Color.BLUE);
        List<Integer> ids = List.of(t0, t1, t2);

        List<TextureAtlas> pages = TextureAtlas.buildMulti(ids, store, 1.0, 1024, null);

        assertFalse(pages.isEmpty());
        Set<Integer> covered = new HashSet<>();
        for (TextureAtlas page : pages) {
            assertNotNull(page);
            assertTrue(page.getWidth() > 0 && page.getHeight() > 0);
            // composePage never reserves a white pixel.
            assertFalse(page.getTextureIds().contains(TextureAtlas.WHITE_PIXEL_TEX_ID));
            covered.addAll(page.getTextureIds());
        }
        assertEquals(new HashSet<>(ids), covered);
    }
}
