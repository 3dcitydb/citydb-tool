/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences (HFT Stuttgart) <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.store;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Registration contract the writer and atlas builder both depend on: URIs
 * dedupe to a stable id, source paths resolve relative to the shared temp
 * directory (where the DB texture exporter writes its files) and return null
 * — never throw — for unknown ids or missing files, and dimension probing
 * reads the header without requiring a full decode.
 */
class TextureStoreTest {

    @Test
    void registrationDedupesByUriAndAssignsStableIds(@TempDir Path tempDir) {
        TextureStore store = new TextureStore(tempDir);
        assertFalse(store.hasTextures());

        int first = store.register("a.png");
        int second = store.register("b.png");
        assertEquals(first, store.register("a.png"));
        assertTrue(first != second);
        assertTrue(store.hasTextures());
    }

    @Test
    void sourcePathResolvesAgainstTempDirAndToleratesMissingFiles(@TempDir Path tempDir)
            throws IOException {
        TextureStore store = new TextureStore(tempDir);
        writePng(tempDir.resolve("present.png"), 8);
        int present = store.register("present.png");
        int absent = store.register("gone.png");

        assertEquals(tempDir.resolve("present.png"), store.getSourcePath(present));
        // Registered but never written to disk, and a never-registered id:
        // both surface as null rather than an exception.
        assertNull(store.getSourcePath(absent));
        assertNull(store.getSourcePath(999));
    }

    @Test
    void readDimensionsProbesHeaderOnly(@TempDir Path tempDir) throws IOException {
        TextureStore store = new TextureStore(tempDir);
        writePng(tempDir.resolve("tex.png"), 32);
        int texId = store.register("tex.png");

        assertArrayEquals(new int[]{32, 32}, store.readDimensions(texId));
        assertNull(store.readDimensions(999));
    }

    private static void writePng(Path file, int size) throws IOException {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        ImageIO.write(image, "png", file.toFile());
    }
}
