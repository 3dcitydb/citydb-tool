/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences (HFT Stuttgart) <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.styling;

import org.citydb.vis.util.ColorUtils;

import java.util.Arrays;

/**
 * Style applied to a CityGML object on the no-appearance render path —
 * i.e. surfaces that carry neither a texture nor an X3DMaterial vertex
 * color. Surfaces with explicit appearance keep their authored look
 * untouched.
 * <p>
 * Instances are owned by {@link ObjectStyleRegistry}: one default plus
 * zero or more per-feature-type overrides. The 3D Tiles writer emits one
 * plain material per distinct {@link DefaultObjectStyle} used by a node;
 * the I3S writer consumes only the registry's default style.
 * <p>
 * Single knob today: {@link #color}, the sRGB RGBA factor written to the
 * plain material's PBR {@code baseColorFactor}. The default is opaque
 * white {@code (1,1,1,1)}, which reproduces the historical "plain white"
 * rendering when no style is configured. Adding more knobs later
 * ({@code metallicFactor}, {@code roughnessFactor}, {@code doubleSided},
 * …) is a pure addition — append a field, a setter, and update
 * {@link #equals(Object)} / {@link #hashCode()} so the encoder's per-style
 * material dedup keeps working.
 * <p>
 * The plain path is always shaded (PBR + per-face NORMAL + Lambertian).
 * An earlier {@code shaded} toggle was explored but dropped — making I3S
 * actually render unlit was only achievable via the non-spec
 * {@code KHR_materials_unlit} extension and the resulting cross-format /
 * cross-client inconsistency wasn't worth the knob. If you need flat
 * thematic colors, route them through X3DMaterial vertex colors instead;
 * that path is unlit by design.
 * <p>
 * Color values are stored in raw sRGB (the user's authoring space). Each
 * writer is responsible for sRGB→linear conversion when the destination
 * format mandates linear color (e.g. glTF {@code baseColorFactor}). See
 * {@link #toLinearRgba()}.
 */
public final class DefaultObjectStyle {
    private static final float[] DEFAULT_COLOR = {1f, 1f, 1f, 1f};

    private float[] color = DEFAULT_COLOR.clone();

    public static DefaultObjectStyle defaults() {
        return new DefaultObjectStyle();
    }

    /**
     * Parse a hex color string of the form {@code #rrggbb} or {@code #rrggbbaa}
     * (the leading {@code #} is optional). Components are sRGB display values
     * in {@code [0, 1]}. Alpha defaults to {@code 1.0} when omitted.
     *
     * @throws IllegalArgumentException if the input length is not 6 or 8 hex
     *                                  digits or contains non-hex characters
     */
    public static DefaultObjectStyle parseColor(String hex) {
        if (hex == null) {
            throw new IllegalArgumentException("Color must not be null.");
        }
        String h = hex.startsWith("#") ? hex.substring(1) : hex;
        if ((h.length() != 6 && h.length() != 8) || !isHex(h)) {
            throw new IllegalArgumentException(
                    "Color must be #rrggbb or #rrggbbaa, got: " + hex);
        }
        int r = Integer.parseInt(h.substring(0, 2), 16);
        int g = Integer.parseInt(h.substring(2, 4), 16);
        int b = Integer.parseInt(h.substring(4, 6), 16);
        int a = h.length() == 8 ? Integer.parseInt(h.substring(6, 8), 16) : 255;
        DefaultObjectStyle style = new DefaultObjectStyle();
        style.color = new float[]{r / 255f, g / 255f, b / 255f, a / 255f};
        return style;
    }

    private static boolean isHex(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            boolean digit = c >= '0' && c <= '9';
            boolean lower = c >= 'a' && c <= 'f';
            boolean upper = c >= 'A' && c <= 'F';
            if (!digit && !lower && !upper) {
                return false;
            }
        }
        return true;
    }

    /**
     * sRGB RGBA color, defensive copy. Use {@link #toLinearRgba()} when
     * writing to glTF / I3S {@code baseColorFactor}.
     */
    public float[] color() {
        return color.clone();
    }

    public DefaultObjectStyle setColor(float r, float g, float b, float a) {
        this.color = new float[]{
                ColorUtils.clampUnit(r),
                ColorUtils.clampUnit(g),
                ColorUtils.clampUnit(b),
                ColorUtils.clampUnit(a)
        };
        return this;
    }

    /**
     * Whether the configured color differs from the default opaque white. When
     * {@code false} the writers can skip emitting {@code baseColorFactor}
     * since the plain material's PBR default already renders white.
     */
    public boolean hasNonDefaultColor() {
        return color[0] != DEFAULT_COLOR[0]
                || color[1] != DEFAULT_COLOR[1]
                || color[2] != DEFAULT_COLOR[2]
                || color[3] != DEFAULT_COLOR[3];
    }

    /**
     * Whether the color has any transparency. Triggers
     * {@code alphaMode=BLEND} on the plain material when {@code true}.
     */
    public boolean hasAlpha() {
        return color[3] < 0.999f;
    }

    /**
     * Convert the sRGB color to linear RGBA suitable for direct use in glTF
     * {@code baseColorFactor} (the glTF spec defines that field in linear
     * color space). RGB channels go through the IEC 61966-2-1 sRGB→linear
     * curve; alpha passes through unchanged.
     */
    public float[] toLinearRgba() {
        return new float[]{
                ColorUtils.srgbToLinear(color[0]),
                ColorUtils.srgbToLinear(color[1]),
                ColorUtils.srgbToLinear(color[2]),
                color[3]
        };
    }

    /**
     * Value equality based on the configured color. Allows the 3D Tiles
     * encoder to dedupe plain materials when distinct feature types resolve
     * to styles with identical color components — the writer emits one
     * material per distinct style, not per distinct type.
     */
    @Override
    public boolean equals(Object o) {
        return o instanceof DefaultObjectStyle other && Arrays.equals(color, other.color);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(color);
    }
}
