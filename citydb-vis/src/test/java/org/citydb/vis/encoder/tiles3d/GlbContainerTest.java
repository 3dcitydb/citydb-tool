/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences (HFT Stuttgart) <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.encoder.tiles3d;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Pins the glTF 2.0 Binary container invariants every GLB consumer checks
 * before parsing: magic {@code glTF}, version 2, total length, 4-byte chunk
 * alignment with space-padded JSON and zero-padded BIN, and the exact chunk
 * type codes ({@code BIN} carries a trailing NUL in its fourCC).
 */
class GlbContainerTest {
    private static final int GLB_MAGIC = 0x46546C67;   // "glTF"
    private static final int CHUNK_JSON = 0x4E4F534A;  // "JSON"
    private static final int CHUNK_BIN = 0x004E4942;   // "BIN\0"

    @Test
    void unalignedChunksArePaddedToFourBytes() {
        byte[] json = "{\"a\":1}".getBytes(StandardCharsets.UTF_8);  // 7 bytes → pad 1
        byte[] bin = {1, 2, 3, 4, 5, 6};                             // 6 bytes → pad 2

        byte[] glb = GlbContainer.assemble(json, bin);
        ByteBuffer buf = ByteBuffer.wrap(glb).order(ByteOrder.LITTLE_ENDIAN);

        assertEquals(GLB_MAGIC, buf.getInt());
        assertEquals(2, buf.getInt(), "GLB version");
        assertEquals(glb.length, buf.getInt(), "declared total length");

        // JSON chunk: padded length, "JSON" fourCC, space padding.
        assertEquals(8, buf.getInt(), "JSON chunk length must be 4-byte aligned");
        assertEquals(CHUNK_JSON, buf.getInt());
        byte[] jsonOut = new byte[8];
        buf.get(jsonOut);
        assertEquals("{\"a\":1} ", new String(jsonOut, StandardCharsets.UTF_8),
                "JSON must be space-padded");

        // BIN chunk: padded length, "BIN\0" fourCC, zero padding.
        assertEquals(8, buf.getInt(), "BIN chunk length must be 4-byte aligned");
        assertEquals(CHUNK_BIN, buf.getInt());
        byte[] binOut = new byte[8];
        buf.get(binOut);
        assertArrayEquals(new byte[]{1, 2, 3, 4, 5, 6, 0, 0}, binOut,
                "BIN must be zero-padded");

        assertFalse(buf.hasRemaining());
        assertEquals(12 + 8 + 8 + 8 + 8, glb.length);
    }

    @Test
    void alignedChunksGetNoPadding() {
        byte[] json = "{\"ab\":12}   ".getBytes(StandardCharsets.UTF_8);  // 12 bytes
        byte[] bin = {1, 2, 3, 4};

        byte[] glb = GlbContainer.assemble(json, bin);
        ByteBuffer buf = ByteBuffer.wrap(glb).order(ByteOrder.LITTLE_ENDIAN);
        buf.position(12);

        assertEquals(json.length, buf.getInt());
        buf.position(buf.position() + 4 + json.length);
        assertEquals(bin.length, buf.getInt());
        assertEquals(12 + 8 + json.length + 8 + bin.length, glb.length);
    }
}
