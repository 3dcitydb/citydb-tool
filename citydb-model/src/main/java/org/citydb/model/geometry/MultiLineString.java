/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
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
    public MultiLineString copy() {
        return new MultiLineString(lineStrings.stream()
                .map(LineString::copy)
                .toArray(LineString[]::new))
                .copyPropertiesFrom(this);
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
