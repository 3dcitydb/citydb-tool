/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.vis.encoder;

import java.nio.charset.StandardCharsets;

public class AttrValueCoercer {
    private static final byte[] EMPTY = new byte[0];

    private AttrValueCoercer() {
    }

    public static int toInt(Object value) {
        return value instanceof Number n ? n.intValue() : 0;
    }

    public static double toDouble(Object value) {
        return value instanceof Number n ? n.doubleValue() : Double.NaN;
    }

    public static byte[] toUtf8(Object value) {
        if (value == null) {
            return EMPTY;
        }
        return value.toString().getBytes(StandardCharsets.UTF_8);
    }
}
