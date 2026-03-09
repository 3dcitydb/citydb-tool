/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.model.appearance;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class TextureCoordinate implements Serializable {
    private float s;
    private float t;

    private TextureCoordinate(float s, float t) {
        this.s = s;
        this.t = t;
    }

    public static TextureCoordinate of(float s, float t) {
        return new TextureCoordinate(s, t);
    }

    public static TextureCoordinate of(double s, double t) {
        return new TextureCoordinate((float) s, (float) t);
    }

    public static List<TextureCoordinate> of(List<? extends Number> coordinates) {
        Objects.requireNonNull(coordinates, "The coordinate list must not be null.");
        if (coordinates.size() % 2 != 0) {
            throw new IllegalArgumentException("The list does not contain pairs of texture coordinates.");
        }

        List<TextureCoordinate> coordinateList = new ArrayList<>(coordinates.size() / 2);
        for (int i = 0; i < coordinates.size(); i += 2) {
            coordinateList.add(new TextureCoordinate(
                    coordinates.get(i).floatValue(),
                    coordinates.get(i + 1).floatValue()));
        }

        return coordinateList;
    }

    public float getS() {
        return s;
    }

    public float getT() {
        return t;
    }

    public TextureCoordinate setS(float s) {
        this.s = s;
        return this;
    }

    public TextureCoordinate setT(float t) {
        this.t = t;
        return this;
    }
}
