/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.styling;

import org.citydb.vis.util.ColorUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Characterization tests for {@link DefaultObjectStyle}, the plain-material
 * colour value object. The two contracts worth pinning, both silent when wrong:
 * hex parsing (a bad parse ships the wrong render colour) and value
 * equality / linear conversion (the 3D Tiles encoder dedups plain materials by
 * {@code equals}/{@code hashCode}, and glTF {@code baseColorFactor} requires
 * sRGB&rarr;linear with alpha passed through untouched).
 */
class DefaultObjectStyleTest {

    @Test
    void parsesHexWithAndWithoutHashAndAlpha() {
        assertArrayEquals(new float[]{1f, 0f, 0f, 1f},
                DefaultObjectStyle.parseColor("#ff0000").color(), 0f);
        // Leading '#' is optional.
        assertArrayEquals(new float[]{0f, 1f, 0f, 1f},
                DefaultObjectStyle.parseColor("00ff00").color(), 0f);
        // 8 digits -> explicit alpha; 6 digits -> alpha defaults to 1.
        assertArrayEquals(new float[]{0f, 0f, 1f, 128 / 255f},
                DefaultObjectStyle.parseColor("#0000ff80").color(), 1e-6f);
    }

    @Test
    void rejectsMalformedHex() {
        for (String bad : new String[]{null, "", "#fff", "#ff00", "#gggggg", "12345", "#0000ff8"}) {
            assertThrows(IllegalArgumentException.class,
                    () -> DefaultObjectStyle.parseColor(bad),
                    "expected reject for: " + bad);
        }
    }

    @Test
    void defaultIsOpaqueWhiteWithNoFlags() {
        DefaultObjectStyle def = DefaultObjectStyle.defaults();
        assertArrayEquals(new float[]{1f, 1f, 1f, 1f}, def.color(), 0f);
        assertFalse(def.hasNonDefaultColor());
        assertFalse(def.hasAlpha());
    }

    @Test
    void flagsReflectColourAndAlpha() {
        assertTrue(DefaultObjectStyle.parseColor("#ff0000").hasNonDefaultColor());
        // White but semi-transparent still differs from the opaque-white default.
        assertTrue(DefaultObjectStyle.parseColor("#ffffff80").hasNonDefaultColor());
        // Alpha just below the 0.999 threshold counts as transparent.
        assertTrue(DefaultObjectStyle.parseColor("#ffffffe0").hasAlpha());
        assertFalse(DefaultObjectStyle.parseColor("#ffffffff").hasAlpha());
    }

    @Test
    void equalsAndHashCodeTrackColourForMaterialDedup() {
        DefaultObjectStyle a = DefaultObjectStyle.parseColor("#123456");
        DefaultObjectStyle b = DefaultObjectStyle.parseColor("123456");
        DefaultObjectStyle c = DefaultObjectStyle.parseColor("#123457");

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, c);
    }

    @Test
    void toLinearRgbaConvertsRgbButPassesAlphaThrough() {
        DefaultObjectStyle style = DefaultObjectStyle.parseColor("#0000ff80");
        float[] linear = style.toLinearRgba();

        // Endpoints are fixed points of the sRGB curve; blue channel matches
        // ColorUtils; alpha is copied verbatim (not gamma-corrected).
        assertEquals(ColorUtils.srgbToLinear(1f), linear[2], 1e-6f);
        assertEquals(0f, linear[0], 0f);
        assertEquals(128 / 255f, linear[3], 1e-6f);
    }

    @Test
    void setColorClampsOutOfRangeAndColourGetterIsDefensive() {
        DefaultObjectStyle style = DefaultObjectStyle.defaults().setColor(2f, -1f, 0.5f, 9f);
        assertArrayEquals(new float[]{1f, 0f, 0.5f, 1f}, style.color(), 0f);

        // color() must hand back a copy — mutating it cannot corrupt the style.
        float[] snapshot = style.color();
        snapshot[0] = 0.123f;
        assertEquals(1f, style.color()[0], 0f);
    }
}
