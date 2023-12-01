/*
 * citydb-tool - Command-line tool for the 3D City Database
 * https://www.3dcitydb.org/
 *
 * Copyright 2022-2023
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

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public abstract class SurfaceCollection<T extends SurfaceCollection<?>> extends Surface<T> {
    private final List<Polygon> polygons;

    SurfaceCollection(List<Polygon> polygons) {
        Objects.requireNonNull(polygons, "The polygon list must not be null.");
        this.polygons = asChild(polygons);
    }

    SurfaceCollection(Polygon... polygons) {
        Objects.requireNonNull(polygons, "The polygon array must not be null.");
        this.polygons = asChild(Arrays.asList(polygons));
    }

    public List<Polygon> getPolygons() {
        return polygons;
    }

    @Override
    public int getVertexDimension() {
        return polygons.stream().anyMatch(polygon -> polygon.getVertexDimension() == 2) ? 2 : 3;
    }

    @Override
    public T force2D() {
        polygons.forEach(Polygon::force2D);
        return self();
    }
}
