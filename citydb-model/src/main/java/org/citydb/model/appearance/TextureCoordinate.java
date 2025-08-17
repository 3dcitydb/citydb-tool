/*
 * citydb-tool - Command-line tool for the 3D City Database
 * https://www.3dcitydb.org/
 *
 * Copyright 2022-2025
 * virtualcitysystems GmbH, Germany
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
