/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.vis.encoder.tiles3d;

import org.citydb.vis.util.BufferUtils;

import java.nio.ByteBuffer;

/**
 * Assembles a glTF 2.0 Binary (GLB) container from its JSON and BIN chunks.
 * <p>
 * GLB layout: 12-byte header, 8-byte JSON chunk header + padded JSON data,
 * 8-byte BIN chunk header + padded BIN data. JSON is space-padded, BIN is
 * zero-padded. Each chunk is 4-byte aligned.
 */
public final class GlbContainer {
    private static final int GLB_MAGIC = 0x46546C67;   // "glTF"
    private static final int GLB_VERSION = 2;
    private static final int CHUNK_JSON = 0x4E4F534A;  // "JSON"
    private static final int CHUNK_BIN = 0x004E4942;   // "BIN\0"

    private GlbContainer() {
    }

    public static byte[] assemble(byte[] jsonData, byte[] binData) {
        int jsonPadding = (4 - (jsonData.length % 4)) % 4;
        int jsonChunkLength = jsonData.length + jsonPadding;
        int binPadding = (4 - (binData.length % 4)) % 4;
        int binChunkLength = binData.length + binPadding;

        int totalLength = 12 + 8 + jsonChunkLength + 8 + binChunkLength;
        ByteBuffer glb = BufferUtils.allocateLittleEndian(totalLength);

        glb.putInt(GLB_MAGIC);
        glb.putInt(GLB_VERSION);
        glb.putInt(totalLength);

        glb.putInt(jsonChunkLength);
        glb.putInt(CHUNK_JSON);
        glb.put(jsonData);
        for (int i = 0; i < jsonPadding; i++) glb.put((byte) ' ');

        glb.putInt(binChunkLength);
        glb.putInt(CHUNK_BIN);
        glb.put(binData);
        for (int i = 0; i < binPadding; i++) glb.put((byte) 0);

        return glb.array();
    }
}
