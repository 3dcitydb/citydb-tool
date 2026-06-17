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
        // Corner vertices (u, v, h) in quantized space. Height ramps with v
        // (latitude): south edge h=0 -> minHeight, north edge h=32767 -> maxHeight.
        int[] u = {0, 32767, 0, 32767};
        int[] v = {0, 0, 32767, 32767};
        int[] h = {0, 0, 32767, 32767};
        // Two triangles tiling the quad: (sw, se, nw) and (se, ne, nw).
        int[] indices = {0, 1, 2, 1, 3, 2};

        ByteBuffer buf = ByteBuffer.allocate(512).order(ByteOrder.LITTLE_ENDIAN);

        // --- 88-byte header: only min/max height (at offset 24) are read.
        buf.position(24);
        buf.putFloat((float) MIN_HEIGHT);
        buf.putFloat((float) MAX_HEIGHT);
        buf.position(88);

        // --- Vertex data: count, then zig-zag + delta encoded u, v, h.
        buf.putInt(u.length);
        putVertexArray(buf, u);
        putVertexArray(buf, v);
        putVertexArray(buf, h);

        // --- Triangle indices (16-bit; vertexCount well under 65536).
        buf.putInt(indices.length / 3);
        putIndexArray(buf, indices);

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

    /** High-watermark encode triangle indices into 16-bit codes (inverse of the decoder). */
    private static void putIndexArray(ByteBuffer buf, int[] indices) {
        int highest = 0;
        for (int index : indices) {
            int code = highest - index;
            buf.putShort((short) code);
            if (code == 0) {
                highest++;
            }
        }
    }
}
