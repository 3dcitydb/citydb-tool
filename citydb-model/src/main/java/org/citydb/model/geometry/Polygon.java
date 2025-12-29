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

import org.citydb.model.common.ChildList;
import org.citydb.model.common.Visitor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class Polygon extends Surface<Polygon> {
    private final LinearRing exteriorRing;
    private List<LinearRing> interiorRings;
    private boolean reversed;

    private Polygon(LinearRing exteriorRing, List<LinearRing> interiorRings, boolean reversed) {
        Objects.requireNonNull(exteriorRing, "The exterior ring must not be null.");
        this.exteriorRing = asChild(exteriorRing);
        this.interiorRings = asChild(interiorRings);
        this.reversed = reversed;
    }

    private Polygon(LinearRing exteriorRing, LinearRing[] interiorRings, boolean reversed) {
        Objects.requireNonNull(exteriorRing, "The exterior ring must not be null.");
        this.exteriorRing = asChild(exteriorRing);
        this.interiorRings = interiorRings != null ? asChild(Arrays.asList(interiorRings)) : null;
        this.reversed = reversed;
    }

    public static Polygon of(LinearRing exteriorRing, List<LinearRing> interiorRings, boolean reversed) {
        return new Polygon(exteriorRing, interiorRings, reversed);
    }

    public static Polygon of(LinearRing exteriorRing, List<LinearRing> interiorRings) {
        return new Polygon(exteriorRing, interiorRings, false);
    }

    public static Polygon of(LinearRing exteriorRing, LinearRing[] interiorRings, boolean reversed) {
        return new Polygon(exteriorRing, interiorRings, reversed);
    }

    public static Polygon of(LinearRing exteriorRing, LinearRing[] interiorRings) {
        return new Polygon(exteriorRing, interiorRings, false);
    }

    public static Polygon of(LinearRing exteriorRing, boolean reversed) {
        return new Polygon(exteriorRing, (List<LinearRing>) null, reversed);
    }

    public static Polygon of(LinearRing exteriorRing) {
        return new Polygon(exteriorRing, (List<LinearRing>) null, false);
    }

    public static Polygon empty() {
        return new Polygon(LinearRing.empty(), (List<LinearRing>) null, false);
    }

    public LinearRing getExteriorRing() {
        return exteriorRing;
    }

    public boolean hasInteriorRings() {
        return interiorRings != null && !interiorRings.isEmpty();
    }

    public List<LinearRing> getInteriorRings() {
        if (interiorRings == null) {
            interiorRings = new ChildList<>(this);
        }

        return interiorRings;
    }

    public List<LinearRing> getRings() {
        int size = hasInteriorRings() ? interiorRings.size() + 1 : 1;
        List<LinearRing> rings = new ArrayList<>(size);

        rings.add(exteriorRing);
        if (size > 1) {
            rings.addAll(interiorRings);
        }

        return rings;
    }

    public boolean isReversed() {
        return reversed;
    }

    public Polygon setReversed(boolean reversed) {
        this.reversed = reversed;
        return this;
    }

    @Override
    public int getVertexDimension() {
        return exteriorRing.getVertexDimension();
    }

    @Override
    public Polygon force2D() {
        exteriorRing.force2D();
        if (hasInteriorRings()) {
            interiorRings.forEach(LinearRing::force2D);
        }

        return this;
    }

    @Override
    public Polygon copy() {
        return new Polygon(exteriorRing.copy(),
                interiorRings != null ? interiorRings.stream()
                        .map(LinearRing::copy)
                        .toArray(LinearRing[]::new) :
                        null,
                reversed)
                .copyPropertiesFrom(this);
    }

    @Override
    public GeometryType getGeometryType() {
        return GeometryType.POLYGON;
    }

    @Override
    public void accept(Visitor visitor) {
        visitor.visit(this);
    }

    @Override
    Polygon self() {
        return this;
    }
}
