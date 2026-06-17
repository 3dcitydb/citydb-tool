/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences (HFT Stuttgart) <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.terrain;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/**
 * Decoder for the <a href="https://github.com/CesiumGS/quantized-mesh">
 * quantized-mesh 1.0</a> tile payload (the {@code .terrain} body, already
 * gunzipped).
 * <p>
 * Decodes the 88-byte header (for the height range), the zig-zag/delta-encoded
 * vertex {@code u}/{@code v}/{@code height} arrays, and the
 * high-watermark-encoded triangle indices into a sampleable
 * {@link QuantizedMeshTile}. The four edge-vertex lists are skipped, and the
 * trailing extension blocks are scanned only for the
 * {@link #METADATA_EXTENSION_ID METADATA} extension — its embedded JSON is
 * returned verbatim so the caller can read the {@code available} tile ranges
 * that drive deep-LOD availability (the {@code octvertexnormals} and
 * {@code watermask} extensions are ignored).
 */
final class QuantizedMeshDecoder {
    private static final int HEADER_BYTES = 88;
    private static final int METADATA_EXTENSION_ID = 4;

    private QuantizedMeshDecoder() {
    }

    /**
     * Geometry plus the raw METADATA-extension JSON (or {@code null} when the
     * tile carries no such extension — e.g. it was not requested with
     * {@code extensions=metadata}, or it is not an availability tile).
     */
    record DecodedTile(QuantizedMeshTile tile, String metadataJson) {
    }

    /**
     * Decode a tile body covering the geographic extent
     * {@code [west, south, east, north]} (degrees).
     *
     * @throws IllegalArgumentException if the buffer is too small or malformed
     */
    static DecodedTile decode(byte[] data,
                              double west, double south, double east, double north) {
        if (data.length < HEADER_BYTES + 4) {
            throw new IllegalArgumentException(
                    "quantized-mesh payload too small (" + data.length + " bytes)");
        }
        ByteBuffer buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);

        // --- Header (88 bytes): center (3 double), min/max height (2 float),
        //     bounding sphere (4 double), horizon occlusion point (3 double).
        buffer.position(24); // skip center
        float minHeight = buffer.getFloat();
        float maxHeight = buffer.getFloat();
        buffer.position(HEADER_BYTES); // skip bounding sphere + horizon point

        // --- Vertex data: count, then u, v, height arrays (zig-zag + delta).
        // Validate the count against the bytes left (each vertex needs 3 × 2 = 6
        // bytes across the u/v/h sections) so a corrupt/truncated tile raises a
        // recoverable IllegalArgumentException rather than allocating a huge or
        // negative array (NegativeArraySizeException, or OutOfMemoryError — an
        // Error that would escape the caller's per-tile recovery).
        int vertexCount = buffer.getInt();
        if (vertexCount < 0 || (long) vertexCount * 6 > buffer.remaining()) {
            throw new IllegalArgumentException(
                    "quantized-mesh vertex count out of range: " + vertexCount);
        }
        int[] u = new int[vertexCount];
        int[] v = new int[vertexCount];
        int[] h = new int[vertexCount];
        decodeVertexArray(buffer, u);
        decodeVertexArray(buffer, v);
        decodeVertexArray(buffer, h);

        // --- Triangle indices. 32-bit when vertexCount > 65536, else 16-bit;
        //     32-bit data is 4-byte aligned (16-bit is implicitly 2-byte
        //     aligned, which the vertex section already guarantees).
        boolean longIndices = vertexCount > 65536;
        int indexBytes = longIndices ? 4 : 2;
        if (longIndices && (buffer.position() & 3) != 0) {
            buffer.position(buffer.position() + 2);
        }
        int triangleCount = buffer.getInt();
        // Same bounds guard as the vertex count: each index is indexBytes wide,
        // three per triangle. The long arithmetic also prevents triangleCount*3
        // from overflowing the int array size below.
        if (triangleCount < 0 || (long) triangleCount * 3 * indexBytes > buffer.remaining()) {
            throw new IllegalArgumentException(
                    "quantized-mesh triangle count out of range: " + triangleCount);
        }
        int[] indices = new int[triangleCount * 3];
        decodeIndexArray(buffer, indices, longIndices);

        QuantizedMeshTile tile = new QuantizedMeshTile(west, south, east, north,
                minHeight, maxHeight, u, v, h, indices);

        // --- Edge-vertex lists (west, south, east, north): each a uint32 count
        //     followed by count indices. Skipped — only their length matters to
        //     reach the extension blocks.
        for (int e = 0; e < 4; e++) {
            int count = buffer.getInt();
            buffer.position(buffer.position() + count * indexBytes);
        }

        // --- Extension blocks: uint8 id, uint32 length, then the body. Scan for
        //     the METADATA extension and return its JSON; ignore the rest.
        String metadataJson = readMetadataExtension(buffer);
        return new DecodedTile(tile, metadataJson);
    }

    private static String readMetadataExtension(ByteBuffer buffer) {
        while (buffer.remaining() >= 5) {
            int extensionId = buffer.get() & 0xFF;
            int extensionLength = buffer.getInt(); // buffer is already LITTLE_ENDIAN
            int bodyStart = buffer.position();
            if (extensionLength < 0 || extensionLength > buffer.remaining()) {
                break; // malformed length — stop scanning rather than overrun
            }

            if (extensionId == METADATA_EXTENSION_ID) {
                int stringLength = buffer.getInt(); // always little-endian
                if (stringLength > 0 && stringLength <= buffer.remaining()) {
                    byte[] json = new byte[stringLength];
                    buffer.get(json);
                    return new String(json, StandardCharsets.UTF_8);
                }
                return null;
            }
            buffer.position(bodyStart + extensionLength);
        }
        return null;
    }

    /**
     * Decode one {@code vertexCount}-length array of unsigned 16-bit values
     * that were zig-zag encoded and delta-encoded against the running value.
     */
    private static void decodeVertexArray(ByteBuffer buffer, int[] out) {
        int value = 0;
        for (int i = 0; i < out.length; i++) {
            int encoded = buffer.getShort() & 0xFFFF;
            value += (encoded >> 1) ^ (-(encoded & 1)); // zig-zag decode + delta
            out[i] = value;
        }
    }

    /**
     * Decode high-watermark-encoded triangle indices into {@code out}.
     */
    private static void decodeIndexArray(ByteBuffer buffer, int[] out, boolean longIndices) {
        int highest = 0;
        for (int i = 0; i < out.length; i++) {
            int code = longIndices ? buffer.getInt() : (buffer.getShort() & 0xFFFF);
            out[i] = highest - code;
            if (code == 0) {
                highest++;
            }
        }
    }
}
