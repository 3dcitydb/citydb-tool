/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.vis.writer;

import org.citydb.core.file.OutputFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Thread-safe texture image registry.
 * <p>
 * During the write phase, texture images are registered (deduplicated by URI)
 * and assigned stable IDs. The actual file I/O is deferred to the close phase
 * because texture BLOBs are written to disk by BlobExporter in batches —
 * the files may not yet exist when {@link #register} is called.
 * <p>
 * During the close phase (after BlobExporter has flushed all batches), texture
 * images are copied from the export output directory to the I3S node structure.
 */
class TextureStore implements Closeable {
    private static final Logger logger = LoggerFactory.getLogger(TextureStore.class);

    private final OutputFile outputFile;
    private final ConcurrentHashMap<String, Integer> uriToId = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, String> idToUri = new ConcurrentHashMap<>();
    private final AtomicInteger nextId = new AtomicInteger(0);

    TextureStore(OutputFile outputFile) {
        this.outputFile = outputFile;
    }

    /**
     * Register a texture image URI. Deduplicates by URI and assigns a stable ID.
     * Does NOT read the file — file I/O is deferred to the close phase.
     *
     * @return texture ID (>= 0)
     */
    int register(String uri) {
        return uriToId.computeIfAbsent(uri, k -> {
            int id = nextId.getAndIncrement();
            idToUri.put(id, uri);
            return id;
        });
    }

    /**
     * Copy a registered texture image to the target path.
     * Resolves the texture URI against the output file's parent directory,
     * matching how BlobExporter writes texture BLOBs.
     * <p>
     * Must be called after BlobExporter has flushed all batches (i.e., after
     * {@code Exporter.closeSession()}).
     */
    private void copyTo(int textureId, Path target) throws IOException {
        String uri = idToUri.get(textureId);
        if (uri == null) return;

        Path source = outputFile.getFile().getParent().resolve(uri);
        if (!Files.exists(source)) return;

        Files.createDirectories(target.getParent());

        // Always convert to JPEG (TYPE_INT_RGB) to match the declared "jpg" format
        // in textureSetDefinitions. Raw file copy would preserve PNG format, causing
        // a format mismatch that viewers cannot decode. Also ensures alpha channels
        // are composited over white instead of producing dark/CMYK artifacts.
        BufferedImage img;
        try {
            img = ImageIO.read(source.toFile());
        } catch (IOException e) {
            logger.warn("Skipping corrupt texture {} ({}): {}", textureId, source, e.getMessage());
            return;
        }
        if (img != null) {
            ImageIO.write(toOpaqueRgb(img), "jpg", target.toFile());
        } else {
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * Copy a registered texture image to the target path, scaling it down
     * by the given factor. If scale >= 1.0, delegates to {@link #copyTo}.
     */
    void copyScaled(int textureId, Path target, double scale) throws IOException {
        if (scale >= 1.0) {
            copyTo(textureId, target);
            return;
        }

        Path source = getSourcePath(textureId);
        if (source == null) return;

        BufferedImage img;
        try {
            img = ImageIO.read(source.toFile());
        } catch (IOException e) {
            logger.warn("Skipping corrupt texture {} ({}): {}", textureId, source, e.getMessage());
            return;
        }
        if (img == null) {
            copyTo(textureId, target);
            return;
        }

        int newWidth = Math.max(1, (int) (img.getWidth() * scale));
        int newHeight = Math.max(1, (int) (img.getHeight() * scale));

        BufferedImage scaled = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = scaled.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, newWidth, newHeight);
        g.drawImage(img, 0, 0, newWidth, newHeight, null);
        g.dispose();

        Files.createDirectories(target.getParent());
        // Always write as JPEG to match declared textureSetDefinitions format
        ImageIO.write(scaled, "jpg", target.toFile());
    }

    /**
     * Get the resolved source file path for a registered texture.
     * Returns null if the texture ID is unknown or the file doesn't exist.
     */
    Path getSourcePath(int textureId) {
        String uri = idToUri.get(textureId);
        if (uri == null) return null;
        Path source = outputFile.getFile().getParent().resolve(uri);
        return Files.exists(source) ? source : null;
    }

    boolean hasTextures() {
        return nextId.get() > 0;
    }

    @Override
    public void close() {
        // No temp files to clean up — file copies go directly to output
    }

    /**
     * Convert a BufferedImage to TYPE_INT_RGB with white background.
     * Prevents alpha-over-black darkening and the Java ARGB→JPEG CMYK bug.
     */
    static BufferedImage toOpaqueRgb(BufferedImage src) {
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
