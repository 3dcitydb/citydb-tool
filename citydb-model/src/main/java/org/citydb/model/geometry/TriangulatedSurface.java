/*
 * citydb-tool - Command-line tool for the 3D City Database
 * https://www.3dcitydb.org/
 *
 * Copyright 2022-2024
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

import java.util.Collections;
import java.util.List;

public class TriangulatedSurface extends SurfaceCollection<TriangulatedSurface> {

    private TriangulatedSurface(List<Polygon> polygons) {
        super(polygons);
    }

    private TriangulatedSurface(Polygon... polygons) {
        super(polygons);
    }

    public static TriangulatedSurface of(List<Polygon> polygons) {
        return new TriangulatedSurface(polygons);
    }

    public static TriangulatedSurface of(Polygon... polygons) {
        return new TriangulatedSurface(polygons);
    }

    public static TriangulatedSurface empty() {
        return new TriangulatedSurface(Collections.emptyList());
    }

    @Override
    public GeometryType getGeometryType() {
        return GeometryType.TRIANGULATED_SURFACE;
    }

    @Override
    public void accept(Visitor visitor) {
        visitor.visit(this);
    }

    @Override
    TriangulatedSurface self() {
        return this;
    }
}
