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

import org.citydb.model.common.Child;
import org.citydb.model.common.Describable;
import org.citydb.model.common.Referencable;
import org.citydb.model.common.Visitable;
import org.citydb.model.walker.ModelWalker;

import java.util.Optional;

public abstract class Geometry<T extends Geometry<?>> extends Child implements SpatialObject, Referencable, Visitable, Describable<GeometryDescriptor> {
    private String objectId;
    private Integer srid;
    private String srsIdentifier;
    private GeometryDescriptor descriptor;

    public abstract GeometryType getGeometryType();

    abstract T self();

    @Override
    public Optional<String> getObjectId() {
        return Optional.ofNullable(objectId);
    }

    @Override
    public T setObjectId(String objectId) {
        this.objectId = objectId;
        return self();
    }

    @Override
    public Optional<Integer> getSRID() {
        if (srid == null) {
            SrsReference parent = getInheritedSrsReference();
            if (parent != null) {
                return parent.getSRID();
            }
        }

        return Optional.ofNullable(srid);
    }

    @Override
    public T setSRID(Integer srid) {
        this.srid = srid;
        return self();
    }

    @Override
    public Optional<String> getSrsIdentifier() {
        if (srsIdentifier == null) {
            SrsReference parent = getInheritedSrsReference();
            if (parent != null) {
                return parent.getSrsIdentifier();
            }
        }

        return Optional.ofNullable(srsIdentifier);
    }

    @Override
    public T setSrsIdentifier(String srsIdentifier) {
        this.srsIdentifier = srsIdentifier;
        return self();
    }

    @Override
    public Optional<GeometryDescriptor> getDescriptor() {
        return Optional.ofNullable(descriptor);
    }

    @Override
    public T setDescriptor(GeometryDescriptor descriptor) {
        this.descriptor = descriptor;
        return self();
    }

    public Geometry<?> getRootGeometry() {
        Geometry<?> root = this, parent = this;
        while ((parent = parent.getParent(Geometry.class)) != null) {
            root = parent;
        }

        return root;
    }

    public T force2D() {
        return self();
    }

    public Envelope getEnvelope() {
        double[] coordinates = new double[]{
                Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE,
                -Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};

        accept(new ModelWalker() {
            @Override
            public void visit(Point point) {
                update(point.getCoordinate());
            }

            @Override
            public void visit(LineString lineString) {
                lineString.getPoints().forEach(this::update);
            }

            @Override
            public void visit(Polygon polygon) {
                polygon.getExteriorRing().getPoints().forEach(this::update);
            }

            private void update(Coordinate coordinate) {
                if (coordinate.getX() < coordinates[0]) {
                    coordinates[0] = coordinate.getX();
                }

                if (coordinate.getY() < coordinates[1]) {
                    coordinates[1] = coordinate.getY();
                }

                if (coordinate.getZ() < coordinates[2]) {
                    coordinates[2] = coordinate.getZ();
                }

                if (coordinate.getX() > coordinates[3]) {
                    coordinates[3] = coordinate.getX();
                }

                if (coordinate.getY() > coordinates[4]) {
                    coordinates[4] = coordinate.getY();
                }

                if (coordinate.getZ() > coordinates[5]) {
                    coordinates[5] = coordinate.getZ();
                }
            }
        });

        Envelope envelope = getVertexDimension() == 2 ?
                Envelope.of(
                        Coordinate.of(coordinates[0], coordinates[1]),
                        Coordinate.of(coordinates[3], coordinates[4])) :
                Envelope.of(
                        Coordinate.of(coordinates[0], coordinates[1], coordinates[2]),
                        Coordinate.of(coordinates[3], coordinates[4], coordinates[5]));

        return envelope
                .setSRID(srid)
                .setSrsIdentifier(srsIdentifier);
    }
}
