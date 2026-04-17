/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.vis.store;

import org.citydb.core.file.OutputFile;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.Closeable;
import java.io.IOException;
import java.lang.ref.SoftReference;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Thread-safe texture image registry.
 * <p>
 * During the write phase, texture images are registered (deduplicated by URI)
 * and assigned stable IDs. The actual file I/O is deferred to the close phase
 * because texture BLOBs are written to disk by the exporter in batches —
 * the files may not yet exist when {@link #register} is called.
 * <p>
 * During the close phase, format-specific writers resolve registered textures
 * via {@link #getSourcePath} and build texture atlases or embed images as
 * needed for their output format.
 */
public class TextureStore implements Closeable {
    private final OutputFile outputFile;
    private final ConcurrentHashMap<String, Integer> uriToId = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, String> idToUri = new ConcurrentHashMap<>();
    private final AtomicInteger nextId = new AtomicInteger(0);
    /**
     * Decoded-image cache keyed by texture ID. SoftReference gives the GC
     * permission to evict under memory pressure; re-reads transparently
     * repopulate. Typical city datasets reuse a small set of facade textures
     * across thousands of nodes, so hitting this cache avoids redundant
     * {@link ImageIO#read} calls without pinning unbounded bitmap memory.
     */
    private final ConcurrentHashMap<Integer, SoftReference<BufferedImage>> imageCache =
            new ConcurrentHashMap<>();

    public TextureStore(OutputFile outputFile) {
        this.outputFile = outputFile;
    }

    /**
     * Register a texture image URI. Deduplicates by URI and assigns a stable ID.
     * Does NOT read the file — file I/O is deferred to the close phase.
     *
     * @return texture ID (>= 0)
     */
    public int register(String uri) {
        return uriToId.computeIfAbsent(uri, k -> {
            int id = nextId.getAndIncrement();
            idToUri.put(id, uri);
            return id;
        });
    }

    /**
     * Get the resolved source file path for a registered texture.
     * Returns null if the texture ID is unknown or the file doesn't exist.
     */
    public Path getSourcePath(int textureId) {
        String uri = idToUri.get(textureId);
        if (uri == null) return null;
        Path source = outputFile.getFile().getParent().resolve(uri);
        return Files.exists(source) ? source : null;
    }

    public boolean hasTextures() {
        return nextId.get() > 0;
    }

    /**
     * Load and decode the source image for a registered texture, caching the
     * result for subsequent calls. Returns {@code null} if the texture ID is
     * unknown, the source file is missing, or decoding fails — callers must
     * null-check and skip the texture on failure.
     */
    public BufferedImage loadImage(int textureId) throws IOException {
        SoftReference<BufferedImage> ref = imageCache.get(textureId);
        BufferedImage cached = ref != null ? ref.get() : null;
        if (cached != null) return cached;

        Path source = getSourcePath(textureId);
        if (source == null) return null;
        BufferedImage img = ImageIO.read(source.toFile());
        if (img != null) {
            imageCache.put(textureId, new SoftReference<>(img));
        }
        return img;
    }

    @Override
    public void close() {
        // No temp files to clean up — file copies go directly to output
    }

    /**
     * Convert a BufferedImage to TYPE_INT_RGB with white background.
     * Prevents alpha-over-black darkening and the Java ARGB→JPEG CMYK bug.
     */
    public static BufferedImage toOpaqueRgb(BufferedImage src) {
        if (src.getType() == BufferedImage.TYPE_INT_RGB) {
            return src;
        }
        BufferedImage rgb = new BufferedImage(src.getWidth(), src.getHeight(),
                BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, src.getWidth(), src.getHeight());
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return rgb;
    }
}
