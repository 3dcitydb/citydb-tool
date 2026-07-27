/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences (HFT Stuttgart) <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.terrain;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Offline unit test for {@link QuantizedMeshDecoder} / {@link QuantizedMeshTile}
 * using hand-built quantized-mesh payloads — no network or ion token required,
 * so it always runs in CI (unlike {@link CesiumWorldTerrainProviderIT}).
 * <p>
 * The synthetic tile is a single quad (two triangles) covering the unit extent
 * {@code [0,0]..[1,1]} degrees with height ramping linearly from {@code 0 m}
 * along the south edge to {@code 100 m} along the north edge, so sampled heights
 * are predictable by hand.
 */
class QuantizedMeshDecoderTest {

    private static final double MIN_HEIGHT = 0.0;
    private static final double MAX_HEIGHT = 100.0;
    private static final double EPS = 1e-3;

    @Test
    void decodesAndSamplesQuadTile() {
        QuantizedMeshTile tile = QuantizedMeshDecoder
                .decode(buildQuadTile(null), 0, 0, 1, 1)
                .tile();

        // Centre of the tile: halfway up the north/south height ramp -> 50 m.
        assertEquals(50.0, tile.sampleHeight(0.5, 0.5), EPS);
        // South edge sits at minHeight, north-west corner vertex at maxHeight.
        assertEquals(MIN_HEIGHT, tile.sampleHeight(0.5, 0.0), EPS);
        assertEquals(MAX_HEIGHT, tile.sampleHeight(0.0, 1.0), EPS);
        // A quarter up the ramp -> 25 m.
        assertEquals(25.0, tile.sampleHeight(0.5, 0.25), EPS);
    }

    @Test
    void returnsNanOutsideTileExtent() {
        QuantizedMeshTile tile = QuantizedMeshDecoder
                .decode(buildQuadTile(null), 0, 0, 1, 1)
                .tile();

        assertTrue(Double.isNaN(tile.sampleHeight(2.0, 0.5)), "east of the tile");
        assertTrue(Double.isNaN(tile.sampleHeight(0.5, -0.5)), "south of the tile");
    }

    @Test
    void parsesMetadataExtension() {
        String json = "{\"available\":[[{\"startX\":0,\"startY\":0,\"endX\":1,\"endY\":0}]]}";
        QuantizedMeshDecoder.DecodedTile decoded =
                QuantizedMeshDecoder.decode(buildQuadTile(json), 0, 0, 1, 1);

        assertEquals(json, decoded.metadataJson());
    }

    @Test
    void metadataJsonIsNullWhenNoExtensionPresent() {
        assertNull(QuantizedMeshDecoder.decode(buildQuadTile(null), 0, 0, 1, 1).metadataJson());
    }

    @Test
    void decodes32BitIndexTileWithAlignmentSkip() {
        // 65537 vertices force the 32-bit index path. The index block then
        // starts at 88 + 4 + 3 * 2 * 65537 = 393314 = 2 (mod 4), so the
        // decoder's 2-byte alignment skip must execute before the triangle
        // count. Geometry is the same quad ramp, so the sampled heights match
        // the 16-bit tile.
        QuantizedMeshTile tile = QuantizedMeshDecoder
                .decode(buildQuadTile(null, 65537), 0, 0, 1, 1)
                .tile();

        assertEquals(50.0, tile.sampleHeight(0.5, 0.5), EPS);
        assertEquals(MIN_HEIGHT, tile.sampleHeight(0.5, 0.0), EPS);
        assertEquals(MAX_HEIGHT, tile.sampleHeight(0.0, 1.0), EPS);
        assertEquals(25.0, tile.sampleHeight(0.5, 0.25), EPS);
    }

    @Test
    void rejectsTruncatedPayload() {
        assertThrows(IllegalArgumentException.class,
                () -> QuantizedMeshDecoder.decode(new byte[16], 0, 0, 1, 1));
    }

    // ---- synthetic tile builder ---------------------------------------------

    /**
     * Build a quantized-mesh 1.0 payload: a quad of four corner vertices in
     * quantized {@code [0,32767]} space, two triangles, no edge vertices, and
     * optionally a trailing METADATA extension carrying {@code metadataJson}.
     */
    private static byte[] buildQuadTile(String metadataJson) {
        return buildQuadTile(metadataJson, 4);
    }

    /**
     * Variant with {@code vertexCount >= 4} vertices: the four quad corners
     * first, then unreferenced filler vertices at the quantized origin. Index
     * width follows the spec — 32-bit (4-byte aligned) when
     * {@code vertexCount > 65536}, else 16-bit.
     */
    private static byte[] buildQuadTile(String metadataJson, int vertexCount) {
        // Corner vertices (u, v, h) in quantized space. Height ramps with v
        // (latitude): south edge h=0 -> minHeight, north edge h=32767 -> maxHeight.
        // Arrays.copyOf pads the fillers with zeros (the sw corner).
        int[] u = Arrays.copyOf(new int[]{0, 32767, 0, 32767}, vertexCount);
        int[] v = Arrays.copyOf(new int[]{0, 0, 32767, 32767}, vertexCount);
        int[] h = Arrays.copyOf(new int[]{0, 0, 32767, 32767}, vertexCount);
        // Two triangles tiling the quad: (sw, se, nw) and (se, ne, nw).
        int[] indices = {0, 1, 2, 1, 3, 2};
        boolean longIndices = vertexCount > 65536;

        ByteBuffer buf = ByteBuffer.allocate(512 + 6 * vertexCount).order(ByteOrder.LITTLE_ENDIAN);

        // --- 88-byte header: only min/max height (at offset 24) are read.
        buf.position(24);
        buf.putFloat((float) MIN_HEIGHT);
        buf.putFloat((float) MAX_HEIGHT);
        buf.position(88);

        // --- Vertex data: count, then zig-zag + delta encoded u, v, h.
        buf.putInt(vertexCount);
        putVertexArray(buf, u);
        putVertexArray(buf, v);
        putVertexArray(buf, h);

        // --- Triangle indices; the 32-bit block is 4-byte aligned, so pad
        //     with two zero bytes when the vertex data ends on a 2-mod-4
        //     position (16-bit needs no padding).
        if (longIndices && (buf.position() & 3) != 0) {
            buf.position(buf.position() + 2);
        }
        buf.putInt(indices.length / 3);
        putIndexArray(buf, indices, longIndices);

        // --- Four edge-vertex lists, all empty.
        for (int e = 0; e < 4; e++) {
            buf.putInt(0);
        }

        // --- Optional METADATA extension (id 4): uint32 length, then the body
        //     (uint32 string length + UTF-8 JSON).
        if (metadataJson != null) {
            byte[] json = metadataJson.getBytes(StandardCharsets.UTF_8);
            buf.put((byte) 4);
            buf.putInt(4 + json.length);
            buf.putInt(json.length);
            buf.put(json);
        }

        return Arrays.copyOf(buf.array(), buf.position());
    }

    /** Delta + zig-zag encode absolute values into 16-bit codes (inverse of the decoder). */
    private static void putVertexArray(ByteBuffer buf, int[] values) {
        int prev = 0;
        for (int value : values) {
            int delta = value - prev;
            buf.putShort((short) ((delta << 1) ^ (delta >> 31)));
            prev = value;
        }
    }

    /** High-watermark encode triangle indices into 16- or 32-bit codes (inverse of the decoder). */
    private static void putIndexArray(ByteBuffer buf, int[] indices, boolean longIndices) {
        int highest = 0;
        for (int index : indices) {
            int code = highest - index;
            if (longIndices) {
                buf.putInt(code);
            } else {
                buf.putShort((short) code);
            }
            if (code == 0) {
                highest++;
            }
        }
    }
}
