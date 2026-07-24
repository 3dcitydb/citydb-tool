/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.model.geometry;

import org.citydb.model.common.Child;
import org.citydb.model.common.ChildList;
import org.citydb.model.util.CopySession;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public abstract class SurfaceCollection<T extends SurfaceCollection<?>> extends Surface<T> {
    private final List<Polygon> polygons;

    SurfaceCollection(int initialCapacity) {
        polygons = new ChildList<>(initialCapacity, this);
    }

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

    @Override
    protected void copyPropertiesTo(Child clone, CopySession session) {
        SurfaceCollection<?> collection = (SurfaceCollection<?>) clone;
        super.copyPropertiesTo(collection, session);

        for (Polygon polygon : polygons) {
            collection.polygons.add(copy(polygon, session));
        }
    }
}
