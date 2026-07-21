/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.appearance;

import org.citydb.vis.store.TextureStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Characterization tests for the atlas builder's quality contract: every
 * texture is scaled by the user's texture scale and no page exceeds the
 * user's max atlas size — regardless of which build path (single-texture
 * fast path, single-page pack, multi-page spill) the texture set takes.
 * The prototype-atlas path of the GPU-instancing feature relies on this
 * contract being path-independent.
 */
class TextureAtlasBuilderTest {

    /** Write a JPEG of the given size and register it, returning its texId. */
    private static int registerTexture(TextureStore store, Path dir, String name,
                                       int width, int height) throws IOException {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.ORANGE);
        g.fillRect(0, 0, width, height);
        g.dispose();
        ImageIO.write(img, "jpg", dir.resolve(name).toFile());
        return store.register(name);
    }

    @Test
    void singleTextureFastPathHonorsTextureScale(@TempDir Path tempDir) throws IOException {
        TextureStore store = new TextureStore(tempDir);
        int texId = registerTexture(store, tempDir, "t0.jpg", 800, 600);

        TextureAtlas atlas = TextureAtlasBuilder.build(List.of(texId), store,
                0.5, 1024, Map.of(), false, AtlasFallbackStrategy.EXPAND);

        assertNotNull(atlas);
        assertEquals(400, atlas.getWidth(), "fast path must apply --texture-scale to width");
        assertEquals(300, atlas.getHeight(), "fast path must apply --texture-scale to height");
        assertEquals(0.5, atlas.getActualScale(), 1e-9,
                "the honored scale must be reported as actualScale");
    }

    @Test
    void singleTextureFastPathClampsToMaxAtlasSize(@TempDir Path tempDir) throws IOException {
        TextureStore store = new TextureStore(tempDir);
        int texId = registerTexture(store, tempDir, "t0.jpg", 512, 512);

        TextureAtlas atlas = TextureAtlasBuilder.build(List.of(texId), store,
                1.0, 256, Map.of(), false, AtlasFallbackStrategy.EXPAND);

        assertNotNull(atlas);
        assertEquals(256, atlas.getWidth(), "512x512 at cap 256 must land exactly on the cap");
        assertEquals(256, atlas.getHeight(), "512x512 at cap 256 must land exactly on the cap");
        // The cap clamp is a size limit, not a --texture-scale violation:
        // actualScale must keep reporting the honored user scale (mirrors
        // buildMulti's unreported per-texture clamp convention), so
        // NodeAssembler.logAtlasViolations stays quiet for this case.
        assertEquals(1.0, atlas.getActualScale(), 1e-9,
                "the maxAtlasSize clamp must not be reported as a scale violation");
    }

    @Test
    void multiPageSpillKeepsEveryPageWithinMaxAtlasSize(@TempDir Path tempDir) throws IOException {
        TextureStore store = new TextureStore(tempDir);
        // Three 200x200 textures cannot fit one 256x256 page at scale 1.0.
        Set<Integer> texIds = new HashSet<>();
        for (int i = 0; i < 3; i++) {
            texIds.add(registerTexture(store, tempDir, "t" + i + ".jpg", 200, 200));
        }

        assertTrue(TextureAtlasBuilder.wouldOverflow(texIds, store, 1.0, 256, Map.of()),
                "three 200x200 textures must overflow a 256x256 page");

        List<TextureAtlas> pages = TextureAtlasBuilder.buildMulti(texIds, store,
                1.0, 256, Map.of());

        assertTrue(pages.size() >= 2, "overflow must spill to multiple pages");
        Set<Integer> packed = new HashSet<>();
        for (TextureAtlas page : pages) {
            assertTrue(page.getWidth() <= 256 && page.getHeight() <= 256,
                    "no page may exceed --max-atlas-size, got "
                            + page.getWidth() + "x" + page.getHeight());
            packed.addAll(page.getTextureIds());
        }
        assertEquals(texIds, packed, "every texture must land on exactly one page");
    }

    @Test
    void expandDoesNotGrowPageWhenTexturesFit(@TempDir Path tempDir) throws IOException {
        TextureStore store = new TextureStore(tempDir);
        Set<Integer> texIds = new HashSet<>();
        texIds.add(registerTexture(store, tempDir, "t0.jpg", 64, 64));
        texIds.add(registerTexture(store, tempDir, "t1.jpg", 64, 64));

        assertFalse(TextureAtlasBuilder.wouldOverflow(texIds, store, 1.0, 256, Map.of()),
                "two 64x64 textures must fit a 256x256 page");

        TextureAtlas atlas = TextureAtlasBuilder.build(texIds, store,
                1.0, 256, Map.of(), false, AtlasFallbackStrategy.EXPAND);

        assertNotNull(atlas);
        assertTrue(atlas.getWidth() <= 256 && atlas.getHeight() <= 256,
                "EXPAND must not grow a page whose textures already fit, got "
                        + atlas.getWidth() + "x" + atlas.getHeight());
        assertEquals(1.0, atlas.getActualScale(), 1e-9,
                "no rescale loop may fire when the textures fit the page");
    }
}
