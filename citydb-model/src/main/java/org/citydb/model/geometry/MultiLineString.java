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

package org.citydb.model.geometry;

import org.citydb.model.common.Visitor;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class MultiLineString extends Geometry<MultiLineString> {
    private final List<LineString> lineStrings;

    private MultiLineString(List<LineString> lineStrings) {
        Objects.requireNonNull(lineStrings, "The line string list must not be null.");
        this.lineStrings = asChild(lineStrings);
    }

    private MultiLineString(LineString[] lineStrings) {
        Objects.requireNonNull(lineStrings, "The line string array must not be null.");
        this.lineStrings = asChild(Arrays.asList(lineStrings));
    }

    public static MultiLineString of(List<LineString> lineStrings) {
        return new MultiLineString(lineStrings);
    }

    public static MultiLineString of(LineString[] lineStrings) {
        return new MultiLineString(lineStrings);
    }

    public static MultiLineString empty() {
        return new MultiLineString(Collections.emptyList());
    }

    public List<LineString> getLineStrings() {
        return lineStrings;
    }

    @Override
    public int getVertexDimension() {
        return lineStrings.stream().anyMatch(lineString -> lineString.getVertexDimension() == 2) ? 2 : 3;
    }

    @Override
    public MultiLineString force2D() {
        lineStrings.forEach(LineString::force2D);
        return this;
    }

    @Override
    public GeometryType getGeometryType() {
        return GeometryType.MULTI_LINE_STRING;
    }

    @Override
    public void accept(Visitor visitor) {
        visitor.visit(this);
    }

    @Override
    MultiLineString self() {
        return this;
    }
}
