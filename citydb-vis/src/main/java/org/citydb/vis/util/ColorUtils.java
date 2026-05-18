/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences (HFT Stuttgart) <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.util;

public class ColorUtils {
    private ColorUtils() {
    }

    /**
     * IEC 61966-2-1 sRGB to linear-light conversion for a single channel in
     * the [0,1] range. Applied per RGB channel when authored colors need to
     * be written into linear-space sinks (e.g. glTF {@code COLOR_0}).
     */
    public static float srgbToLinear(float c) {
        if (c <= 0.04045f) {
            return c / 12.92f;
        }
        return (float) Math.pow((c + 0.055f) / 1.055f, 2.4);
    }

    /**
     * Clamp a float to the closed unit interval {@code [0,1]}.
     */
    public static float clampUnit(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }
}
