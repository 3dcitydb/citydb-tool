/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.attribute;

import org.citydb.vis.model.AttrType;
import org.citydb.vis.model.FeatureData;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Function;

public class AttributeValueCoercer {
    private static final byte[] EMPTY = new byte[0];

    private AttributeValueCoercer() {
    }

    /**
     * Extract a column's values with the coercion appropriate to its
     * {@link AttrType} and hand them to the matching writer. Centralises the
     * type→extractor mapping (notably that both {@code OID} and {@code INT}
     * use the int path) so the I3S and 3D Tiles attribute encoders share one
     * source of truth. The switch is exhaustive: adding an {@code AttrType}
     * becomes a compile error here rather than a silent per-format divergence.
     *
     * @param onInt    receives the {@code int[]} for OID / INT columns
     * @param onDouble receives the {@code double[]} for DOUBLE columns
     * @param onUtf8   receives the {@code byte[][]} for STRING columns
     */
    public static <R> R dispatchByType(AttrType type, List<FeatureData> features, String name,
                                       Function<int[], R> onInt,
                                       Function<double[], R> onDouble,
                                       Function<byte[][], R> onUtf8) {
        return switch (type) {
            case OID, INT -> onInt.apply(extractInts(features, name));
            case DOUBLE -> onDouble.apply(extractDoubles(features, name));
            case STRING -> onUtf8.apply(extractUtf8(features, name));
        };
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
