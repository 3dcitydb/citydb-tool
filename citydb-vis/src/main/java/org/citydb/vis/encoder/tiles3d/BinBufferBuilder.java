/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences <https://www.hft-stuttgart.de>
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
 * Each {@code addX} variant carries a target hint (ARRAY_BUFFER for vertex
 * attributes, ELEMENT_ARRAY_BUFFER for indices, none otherwise) — callers pick
 * by choosing the matching method.
 */
public class BinBufferBuilder {
    private final ByteArrayOutputStream data = new ByteArrayOutputStream();
    private final List<GltfBufferView> bufferViews = new ArrayList<>();

    /** Add a float32 vertex-attribute array. */
    public int addFloat32Array(float[] values) {
        return appendFloat32(values, GltfBufferView.TARGET_ARRAY_BUFFER);
    }

    /** Add a uint32/int32 index array. */
    public int addUint32Array(int[] values) {
        return appendInt32(values, GltfBufferView.TARGET_ELEMENT_ARRAY_BUFFER);
    }

    /** Add an int32 / uint32 property-table column (no GPU target). */
    public int addInt32Array(int[] values) {
        return appendInt32(values, GltfBufferView.TARGET_NONE);
    }

    /** Add a float64 property-table column (no GPU target). */
    public int addFloat64Array(double[] values) {
        align(8);
        int offset = data.size();
        int byteLength = values.length * 8;
        ByteBuffer buf = BufferUtils.allocateLittleEndian(byteLength);
        for (double v : values) buf.putDouble(v);
        data.writeBytes(buf.array());
        bufferViews.add(new GltfBufferView(offset, byteLength, GltfBufferView.TARGET_NONE));
        return bufferViews.size() - 1;
    }

    /** Add raw bytes (e.g., JPEG image data, packed UTF-8 strings). */
    public int addRawBytes(byte[] bytes) {
        align4();
        int offset = data.size();
        data.writeBytes(bytes);
        bufferViews.add(new GltfBufferView(offset, bytes.length, GltfBufferView.TARGET_NONE));
        return bufferViews.size() - 1;
    }

    public List<GltfBufferView> getBufferViews() {
        return bufferViews;
    }

    public byte[] toByteArray() {
        return data.toByteArray();
    }

    private int appendFloat32(float[] values, int target) {
        align4();
        int offset = data.size();
        int byteLength = values.length * 4;
        ByteBuffer buf = BufferUtils.allocateLittleEndian(byteLength);
        for (float v : values) buf.putFloat(v);
        data.writeBytes(buf.array());
        bufferViews.add(new GltfBufferView(offset, byteLength, target));
        return bufferViews.size() - 1;
    }

    private int appendInt32(int[] values, int target) {
        align4();
        int offset = data.size();
        int byteLength = values.length * 4;
        ByteBuffer buf = BufferUtils.allocateLittleEndian(byteLength);
        for (int v : values) buf.putInt(v);
        data.writeBytes(buf.array());
        bufferViews.add(new GltfBufferView(offset, byteLength, target));
        return bufferViews.size() - 1;
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
