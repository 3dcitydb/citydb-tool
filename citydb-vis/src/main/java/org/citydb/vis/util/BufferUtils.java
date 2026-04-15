/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
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
}
