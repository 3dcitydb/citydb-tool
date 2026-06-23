/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences (HFT Stuttgart) <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.store;

import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.Closeable;
import java.io.IOException;
import java.lang.ref.SoftReference;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
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
    private final Path tempDir;
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

    public TextureStore(Path tempDir) {
        this.tempDir = tempDir;
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
        // The DB texture exporter writes BLOBs into the shared temp directory
        // (its OutputFile is rooted there), so registered URIs are relative to
        // tempDir — resolve against it, not the final output file's parent.
        Path source = tempDir.resolve(uri);
        return Files.exists(source) ? source : null;
    }

    public boolean hasTextures() {
        return nextId.get() > 0;
    }

    /**
     * Full-resolution decode with soft-reference caching. Reached via
     * {@link #loadImage(int, int)} when the caller asks for no subsampling.
     * Returns {@code null} if the texture ID is unknown, the source file is
     * missing, or decoding fails — callers must null-check and skip.
     */
    private BufferedImage loadImage(int textureId) throws IOException {
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

    /**
     * Read only the image dimensions without decoding pixel data. Returns
     * {@code int[]{width, height}} or {@code null} if the texture is unknown
     * or the file cannot be parsed.
     * <p>
     * Used by the atlas builder to plan packing before committing to full
     * decode — avoids holding every source {@link BufferedImage} in memory
     * during the dimension-gathering phase.
     */
    public int[] readDimensions(int textureId) throws IOException {
        Path source = getSourcePath(textureId);
        if (source == null) return null;
        try (ImageInputStream iis = ImageIO.createImageInputStream(source.toFile())) {
            if (iis == null) return null;
            Iterator<ImageReader> it = ImageIO.getImageReaders(iis);
            if (!it.hasNext()) return null;
            ImageReader reader = it.next();
            try {
                reader.setInput(iis, true, true);
                return new int[]{reader.getWidth(0), reader.getHeight(0)};
            } finally {
                reader.dispose();
            }
        }
    }

    /**
     * Load the source image at a reduced resolution using decoder-level
     * subsampling. {@code subsample} is the period (every Nth pixel / row);
     * 1 = full resolution, 2 = half, 4 = quarter, 8 = eighth. For JPEG,
     * powers of two up to 8 engage the libjpeg DCT fast path, so decoding
     * a 16K source at subsample=8 costs ~1/64 the CPU and memory of a full
     * decode and never allocates the full-resolution bitmap.
     * <p>
     * Subsampled decodes are not cached — callers should use them immediately
     * and let them fall out of scope. The {@code subsample <= 1} fall-through
     * hits the soft-reference cache.
     */
    public BufferedImage loadImage(int textureId, int subsample) throws IOException {
        if (subsample <= 1) {
            return loadImage(textureId);
        }
        Path source = getSourcePath(textureId);
        if (source == null) return null;
        try (ImageInputStream iis = ImageIO.createImageInputStream(source.toFile())) {
            if (iis == null) return null;
            Iterator<ImageReader> it = ImageIO.getImageReaders(iis);
            if (!it.hasNext()) return null;
            ImageReader reader = it.next();
            try {
                reader.setInput(iis, true, true);
                ImageReadParam param = reader.getDefaultReadParam();
                param.setSourceSubsampling(subsample, subsample, 0, 0);
                return reader.read(0, param);
            } finally {
                reader.dispose();
            }
        }
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
