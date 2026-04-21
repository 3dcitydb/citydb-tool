/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.encoder;

import org.citydb.vis.model.FeatureData;

import java.nio.charset.StandardCharsets;
import java.util.List;

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

    public static int[] extractInts(List<FeatureData> features, String name) {
        int[] out = new int[features.size()];
        for (int i = 0; i < features.size(); i++) {
            FeatureData fd = features.get(i);
            out[i] = fd != null ? toInt(fd.getFieldValue(name)) : 0;
        }
        return out;
    }

    public static double[] extractDoubles(List<FeatureData> features, String name) {
        double[] out = new double[features.size()];
        for (int i = 0; i < features.size(); i++) {
            FeatureData fd = features.get(i);
            out[i] = fd != null ? toDouble(fd.getFieldValue(name)) : Double.NaN;
        }
        return out;
    }

    public static byte[][] extractUtf8(List<FeatureData> features, String name) {
        byte[][] out = new byte[features.size()][];
        for (int i = 0; i < features.size(); i++) {
            FeatureData fd = features.get(i);
            out[i] = fd != null ? toUtf8(fd.getFieldValue(name)) : EMPTY;
        }
        return out;
    }
}
