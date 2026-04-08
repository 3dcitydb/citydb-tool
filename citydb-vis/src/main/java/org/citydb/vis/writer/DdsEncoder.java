/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.vis.writer;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Pure Java encoder that writes texture images as DDS files with BC1 (S3TC DXT1)
 * GPU-compressed textures. No external tools or native libraries required.
 * <p>
 * BC1 reduces GPU memory by 6:1 compared to JPEG (which is decoded to RGBA on
 * the GPU). A 1024x1024 JPEG consumes 4 MB of GPU memory; the same image as
 * BC1 uses only 0.5 MB — directly uploaded without CPU-side decompression.
 * <p>
 * DDS is the GPU-compressed texture format used by Esri's production I3S
 * services (e.g., San Francisco Buildings). CesiumJS natively supports
 * {@code image/vnd-ms.dds} for I3S layers via the
 * {@code WEBGL_compressed_texture_s3tc} WebGL extension (universal on desktop).
 */
class DdsEncoder {

    // DDS header constants
    private static final int DDS_MAGIC = 0x20534444;  // "DDS "
    private static final int HEADER_SIZE = 124;
    private static final int DDSD_CAPS = 0x1;
    private static final int DDSD_HEIGHT = 0x2;
    private static final int DDSD_WIDTH = 0x4;
    private static final int DDSD_PIXELFORMAT = 0x1000;
    private static final int DDSD_MIPMAPCOUNT = 0x20000;
    private static final int DDSD_LINEARSIZE = 0x80000;
    private static final int DDPF_FOURCC = 0x4;
    private static final int DXT1_FOURCC = 0x31545844;  // "DXT1"
    private static final int DDSCAPS_COMPLEX = 0x8;
    private static final int DDSCAPS_TEXTURE = 0x1000;
    private static final int DDSCAPS_MIPMAP = 0x400000;

    /**
     * Encode a BufferedImage as a DDS file with BC1 (DXT1) compression
     * and a full mipmap chain (matching Esri's production I3S services).
     * Input should be TYPE_INT_RGB (opaque, no alpha).
     */
    static void writeImage(BufferedImage image, Path output) throws IOException {
        int width = image.getWidth();
        int height = image.getHeight();
        // BC1 requires dimensions to be multiples of 4 for WebGL compressedTexImage2D
        int paddedW = (width + 3) & ~3;
        int paddedH = (height + 3) & ~3;

        // Generate full mipmap chain: level 0 = original, then halved until 1x1
        int mipCount = 1 + (int) Math.floor(Math.log(Math.max(paddedW, paddedH)) / Math.log(2));
        ByteArrayOutputStream allBc1 = new ByteArrayOutputStream();

        BufferedImage current = image;
        int mw = paddedW, mh = paddedH;
        for (int level = 0; level < mipCount; level++) {
            allBc1.write(encodeBc1(current, mw, mh));
            // Next mip level: halve dimensions (minimum 1)
            int nextW = Math.max(1, mw >> 1);
            int nextH = Math.max(1, mh >> 1);
            if (level + 1 < mipCount) {
                current = downsample(current, nextW, nextH);
            }
            mw = nextW;
            mh = nextH;
        }

        byte[] dds = buildDds(paddedW, paddedH, mipCount, allBc1.toByteArray());
        Files.write(output, dds);
    }

    /**
     * Downsample an image to the given dimensions using bilinear interpolation.
     */
    private static BufferedImage downsample(BufferedImage src, int targetW, int targetH) {
        BufferedImage dst = new BufferedImage(targetW, targetH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = dst.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(src, 0, 0, targetW, targetH, null);
        g.dispose();
        return dst;
    }

    // ---- DDS container writer ------------------------------------------------

    /**
     * Build a complete DDS file: 4-byte magic + 124-byte header + BC1 mipmap data.
     */
    private static byte[] buildDds(int width, int height, int mipCount, byte[] bc1Data) {
        ByteBuffer buf = ByteBuffer.allocate(128 + bc1Data.length);
        buf.order(ByteOrder.LITTLE_ENDIAN);

        int flags = DDSD_CAPS | DDSD_HEIGHT | DDSD_WIDTH | DDSD_PIXELFORMAT | DDSD_LINEARSIZE
                | DDSD_MIPMAPCOUNT;
        int caps = DDSCAPS_COMPLEX | DDSCAPS_TEXTURE | DDSCAPS_MIPMAP;

        // Level 0 linear size for dwPitchOrLinearSize
        int level0Size = Math.max(1, (width + 3) / 4) * Math.max(1, (height + 3) / 4) * 8;

        // Magic
        buf.putInt(DDS_MAGIC);

        // Header (124 bytes)
        buf.putInt(HEADER_SIZE);
        buf.putInt(flags);
        buf.putInt(height);
        buf.putInt(width);
        buf.putInt(level0Size);      // dwPitchOrLinearSize
        buf.putInt(0);               // dwDepth
        buf.putInt(mipCount);        // dwMipMapCount
        for (int i = 0; i < 11; i++) buf.putInt(0);  // dwReserved1[11]

        // Pixel format (32 bytes)
        buf.putInt(32);              // pfSize
        buf.putInt(DDPF_FOURCC);     // pfFlags
        buf.putInt(DXT1_FOURCC);     // pfFourCC = "DXT1"
        buf.putInt(0);               // pfRGBBitCount
        buf.putInt(0);               // pfRBitMask
        buf.putInt(0);               // pfGBitMask
        buf.putInt(0);               // pfBBitMask
        buf.putInt(0);               // pfABitMask

        // Caps
        buf.putInt(caps);            // dwCaps
        buf.putInt(0);               // dwCaps2
        buf.putInt(0);               // dwCaps3
        buf.putInt(0);               // dwCaps4
        buf.putInt(0);               // dwReserved2

        // BC1 image data (all mip levels concatenated)
        buf.put(bc1Data);

        return buf.array();
    }

    // ---- BC1 (DXT1) encoder ---------------------------------------------------

    /**
     * Encode a BufferedImage to BC1 (S3TC DXT1) compressed blocks.
     * Each 4x4 pixel block is compressed to 8 bytes (6:1 vs RGBA8).
     *
     * @param paddedW padded width (multiple of 4, >= image width)
     * @param paddedH padded height (multiple of 4, >= image height)
     */
    private static byte[] encodeBc1(BufferedImage image, int paddedW, int paddedH) {
        int width = image.getWidth();
        int height = image.getHeight();
        int bw = Math.max(1, (paddedW + 3) >> 2);
        int bh = Math.max(1, (paddedH + 3) >> 2);
        byte[] out = new byte[bw * bh * 8];

        int[] block = new int[16];

        for (int by = 0; by < bh; by++) {
            for (int bx = 0; bx < bw; bx++) {
                for (int y = 0; y < 4; y++) {
                    int py = Math.min(by * 4 + y, height - 1);
                    for (int x = 0; x < 4; x++) {
                        int px = Math.min(bx * 4 + x, width - 1);
                        block[y * 4 + x] = image.getRGB(px, py);
                    }
                }
                encodeBlock(block, out, (by * bw + bx) * 8);
            }
        }
        return out;
    }

    /**
     * Encode a single 4x4 pixel block to 8 bytes of BC1 data.
     * Uses bounding-box color selection with inset optimization.
     */
    private static void encodeBlock(int[] pixels, byte[] out, int offset) {
        int minR = 255, minG = 255, minB = 255;
        int maxR = 0, maxG = 0, maxB = 0;
        for (int argb : pixels) {
            int r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
            if (r < minR) minR = r; if (r > maxR) maxR = r;
            if (g < minG) minG = g; if (g > maxG) maxG = g;
            if (b < minB) minB = b; if (b > maxB) maxB = b;
        }

        // Inset bounding box for better quality
        int inR = (maxR - minR) >> 4, inG = (maxG - minG) >> 4, inB = (maxB - minB) >> 4;
        maxR = Math.max(0, maxR - inR); maxG = Math.max(0, maxG - inG); maxB = Math.max(0, maxB - inB);
        minR = Math.min(255, minR + inR); minG = Math.min(255, minG + inG); minB = Math.min(255, minB + inB);

        int color0 = rgb565(maxR, maxG, maxB);
        int color1 = rgb565(minR, minG, minB);

        // Ensure color0 > color1 for 4-color opaque mode
        if (color0 < color1) {
            int t = color0; color0 = color1; color1 = t;
            t = minR; minR = maxR; maxR = t;
            t = minG; minG = maxG; maxG = t;
            t = minB; minB = maxB; maxB = t;
        }

        // 4-entry interpolated palette
        int[] pr = {maxR, minR, (2 * maxR + minR + 1) / 3, (maxR + 2 * minR + 1) / 3};
        int[] pg = {maxG, minG, (2 * maxG + minG + 1) / 3, (maxG + 2 * minG + 1) / 3};
        int[] pb = {maxB, minB, (2 * maxB + minB + 1) / 3, (maxB + 2 * minB + 1) / 3};

        int indices = 0;
        for (int i = 0; i < 16; i++) {
            int r = (pixels[i] >> 16) & 0xFF, g = (pixels[i] >> 8) & 0xFF, b = pixels[i] & 0xFF;
            int bestIdx = 0, bestDist = Integer.MAX_VALUE;
            for (int j = 0; j < 4; j++) {
                int dr = r - pr[j], dg = g - pg[j], db = b - pb[j];
                int dist = dr * dr + dg * dg + db * db;
                if (dist < bestDist) { bestDist = dist; bestIdx = j; }
            }
            indices |= (bestIdx << (i * 2));
        }

        out[offset]     = (byte) color0;
        out[offset + 1] = (byte) (color0 >> 8);
        out[offset + 2] = (byte) color1;
        out[offset + 3] = (byte) (color1 >> 8);
        out[offset + 4] = (byte) indices;
        out[offset + 5] = (byte) (indices >> 8);
        out[offset + 6] = (byte) (indices >> 16);
        out[offset + 7] = (byte) (indices >> 24);
    }

    private static int rgb565(int r, int g, int b) {
        return ((r >> 3) << 11) | ((g >> 2) << 5) | (b >> 3);
    }
}
