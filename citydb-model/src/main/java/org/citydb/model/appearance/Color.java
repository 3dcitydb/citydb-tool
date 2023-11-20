/*
 * citydb-tool - Command-line tool for the 3D City Database
 * https://www.3dcitydb.org/
 *
 * Copyright 2022-2023
 * Virtual City Systems, Germany
 * https://vc.systems/
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.citydb.model.appearance;

import java.io.Serializable;
import java.util.Objects;

public class Color implements Serializable {
    private final double red;
    private final double green;
    private final double blue;
    private final double alpha;

    private Color(double red, double green, double blue, double alpha) {
        this.red = validate(red);
        this.green = validate(green);
        this.blue = validate(blue);
        this.alpha = validate(alpha);
    }

    public static Color of(double red, double green, double blue, double alpha) {
        return new Color(red, green, blue, alpha);
    }

    public static Color of(double red, double green, double blue) {
        return new Color(red, green, blue, 1);
    }

    public static Color of(int red, int green, int blue, double alpha) {
        return new Color(red / 255.0, green / 255.0, blue / 255.0, alpha);
    }

    public static Color of(int red, int green, int blue, int alpha) {
        return of(red, green, blue, alpha / 255.0);
    }

    public static Color of(int red, int green, int blue) {
        return of(red, green, blue, 1);
    }

    public static Color ofHexString(String rgba) {
        String input = Objects.requireNonNull(rgba, "The RGB/A string must not be null.");
        if (input.startsWith("#")) {
            input = input.substring(1);
        }

        if (input.length() != 6 && input.length() != 8) {
            throw new IllegalArgumentException("The RGB/A string must consist of 6 or 8 hex digits.");
        }

        try {
            return Color.of(Integer.parseInt(input.substring(0, 2), 16),
                    Integer.parseInt(input.substring(2, 4), 16),
                    Integer.parseInt(input.substring(4, 6), 16),
                    input.length() == 8 ? Integer.parseInt(input.substring(6, 8), 16) : 255);
        } catch (Exception e) {
            throw new IllegalArgumentException("The RGB/A string " + rgba + " is not a valid hex string.");
        }
    }

    public double getRed() {
        return red;
    }

    public int getRedAsInt() {
        return toInt(red);
    }

    public double getGreen() {
        return green;
    }

    public int getGreenAsInt() {
        return toInt(green);
    }

    public double getBlue() {
        return blue;
    }

    public double getBlueAsInt() {
        return toInt(blue);
    }

    public double getAlpha() {
        return alpha;
    }

    public double getAlphaAsInt() {
        return toInt(alpha);
    }

    public String toRGB() {
        return "#" + toHexString(red) + toHexString(green) + toHexString(blue);
    }

    public String toRGBA() {
        return toRGB() + toHexString(alpha);
    }

    private String toHexString(double value) {
        return String.format("%02X", toInt(value));
    }

    private int toInt(double value) {
        return (int) (value * 255 + 0.5);
    }

    private double validate(double value) {
        return Math.max(Math.min(value, 1), 0);
    }
}
