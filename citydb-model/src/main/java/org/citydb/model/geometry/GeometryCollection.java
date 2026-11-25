/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.model.geometry;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class GeometryCollection implements SpatialObject {
    private final List<Geometry<?>> geometries;
    private Integer srid;
    private String srsIdentifier;

    private GeometryCollection(List<Geometry<?>> geometries) {
        this.geometries = geometries;
    }

    public static GeometryCollection of(List<? extends Geometry<?>> geometries) {
        return new GeometryCollection(List.copyOf(Objects.requireNonNull(geometries,
                "The geometry list must not be null.")));
    }

    public List<Geometry<?>> getGeometries() {
        return geometries;
    }

    @Override
    public int getVertexDimension() {
        return geometries.stream().anyMatch(geometry -> geometry.getVertexDimension() == 2) ? 2 : 3;
    }

    @Override
    public Optional<Integer> getSRID() {
        return Optional.ofNullable(srid);
    }

    @Override
    public GeometryCollection setSRID(Integer srid) {
        this.srid = srid;
        return this;
    }

    @Override
    public Optional<String> getSrsIdentifier() {
        return Optional.ofNullable(srsIdentifier);
    }

    @Override
    public GeometryCollection setSrsIdentifier(String srsIdentifier) {
        this.srsIdentifier = srsIdentifier;
        return this;
    }
}
