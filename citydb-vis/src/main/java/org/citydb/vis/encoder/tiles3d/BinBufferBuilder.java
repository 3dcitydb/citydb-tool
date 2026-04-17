/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.vis.encoder.tiles3d;

import org.citydb.vis.util.BufferUtils;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds the BIN chunk data of a glTF 2.0 Binary (GLB) file by sequentially
 * appending typed arrays or raw byte ranges. Each segment is aligned to its
 * component boundary. Tracks the resulting buffer view metadata
 * ({@link GltfBufferView}) so glTF accessors can reference them by index.
 */
public class BinBufferBuilder {
    private final ByteArrayOutputStream data = new ByteArrayOutputStream();
    private final List<GltfBufferView> bufferViews = new ArrayList<>();

    /** Add a float32 array. Returns the buffer view index. */
    public int addFloat32Array(float[] values) {
        align4();
        int offset = data.size();
        int byteLength = values.length * 4;
        ByteBuffer buf = BufferUtils.allocateLittleEndian(byteLength);
        for (float v : values) buf.putFloat(v);
        data.writeBytes(buf.array());
        bufferViews.add(new GltfBufferView(offset, byteLength));
        return bufferViews.size() - 1;
    }

    /** Add a uint32/int32 array. Returns the buffer view index. */
    public int addUint32Array(int[] values) {
        align4();
        int offset = data.size();
        int byteLength = values.length * 4;
        ByteBuffer buf = BufferUtils.allocateLittleEndian(byteLength);
        for (int v : values) buf.putInt(v);
        data.writeBytes(buf.array());
        bufferViews.add(new GltfBufferView(offset, byteLength));
        return bufferViews.size() - 1;
    }

    /** Add an int32 array (same binary layout as uint32). */
    public int addInt32Array(int[] values) {
        return addUint32Array(values);
    }

    /** Add a float64 array (8-byte aligned). Returns the buffer view index. */
    public int addFloat64Array(double[] values) {
        align(8);
        int offset = data.size();
        int byteLength = values.length * 8;
        ByteBuffer buf = BufferUtils.allocateLittleEndian(byteLength);
        for (double v : values) buf.putDouble(v);
        data.writeBytes(buf.array());
        bufferViews.add(new GltfBufferView(offset, byteLength));
        return bufferViews.size() - 1;
    }

    /** Add raw bytes (e.g., JPEG image data). Returns the buffer view index. */
    public int addRawBytes(byte[] bytes) {
        align4();
        int offset = data.size();
        data.writeBytes(bytes);
        bufferViews.add(new GltfBufferView(offset, bytes.length));
        return bufferViews.size() - 1;
    }

    public List<GltfBufferView> getBufferViews() {
        return bufferViews;
    }

    public int size() {
        return data.size();
    }

    public byte[] toByteArray() {
        return data.toByteArray();
    }

    private void align4() {
        align(4);
    }

    private void align(int boundary) {
        int pad = BufferUtils.paddingFor(data.size(), boundary);
        if (pad > 0) data.write(PAD_ZEROS, 0, pad);
    }

    private static final byte[] PAD_ZEROS = new byte[8];
}
