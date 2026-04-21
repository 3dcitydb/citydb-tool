/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences (HFT Stuttgart) <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.util;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class BufferUtils {
    private BufferUtils() {
    }

    public static ByteBuffer allocateLittleEndian(int capacity) {
        return ByteBuffer.allocate(capacity).order(ByteOrder.LITTLE_ENDIAN);
    }

    /**
     * Return the number of padding bytes needed to advance {@code offset} to
     * the next multiple of {@code boundary}. Returns 0 when already aligned.
     */
    public static int paddingFor(int offset, int boundary) {
        return (boundary - (offset % boundary)) % boundary;
    }
}
