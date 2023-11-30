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

package org.citydb.model.property;

import org.citydb.model.common.InlineOrByReferenceProperty;
import org.citydb.model.common.Name;
import org.citydb.model.common.Reference;
import org.citydb.model.geometry.ImplicitGeometry;
import org.citydb.model.geometry.Point;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class ImplicitGeometryProperty extends Property<ImplicitGeometryProperty> implements InlineOrByReferenceProperty<ImplicitGeometry> {
    private final ImplicitGeometry implicitGeometry;
    private final Reference reference;
    private List<Double> transformationMatrix;
    private Point referencePoint;
    private String lod;

    private ImplicitGeometryProperty(Name name, ImplicitGeometry implicitGeometry) {
        super(name, DataType.IMPLICIT_GEOMETRY_PROPERTY);
        Objects.requireNonNull(implicitGeometry, "The implicit geometry must not be null.");
        this.implicitGeometry = asChild(implicitGeometry);
        reference = null;
    }

    private ImplicitGeometryProperty(Name name, Reference reference) {
        super(name, DataType.IMPLICIT_GEOMETRY_PROPERTY);
        Objects.requireNonNull(reference, "The reference must not be null.");
        this.reference = asChild(reference);
        implicitGeometry = null;
    }

    public static ImplicitGeometryProperty of(Name name, ImplicitGeometry implicitGeometry) {
        return new ImplicitGeometryProperty(name, implicitGeometry);
    }

    public static ImplicitGeometryProperty of(Name name, Reference reference) {
        return new ImplicitGeometryProperty(name, reference);
    }

    @Override
    public Optional<ImplicitGeometry> getObject() {
        return Optional.ofNullable(implicitGeometry);
    }

    @Override
    public Optional<Reference> getReference() {
        return Optional.ofNullable(reference);
    }

    public Optional<List<Double>> getTransformationMatrix() {
        return Optional.ofNullable(transformationMatrix);
    }

    public ImplicitGeometryProperty setTransformationMatrix(List<Double> transformationMatrix) {
        this.transformationMatrix = transformationMatrix != null && transformationMatrix.size() > 15 ?
                new ArrayList<>(transformationMatrix.subList(0, 16)) :
                null;

        return this;
    }

    public Optional<Point> getReferencePoint() {
        return Optional.ofNullable(referencePoint);
    }

    public ImplicitGeometryProperty setReferencePoint(Point referencePoint) {
        this.referencePoint = asChild(referencePoint);
        return this;
    }

    public Optional<String> getLod() {
        return Optional.ofNullable(lod);
    }

    public ImplicitGeometryProperty setLod(String lod) {
        this.lod = lod;
        return this;
    }

    public ImplicitGeometryProperty setLod(int lod) {
        this.lod = String.valueOf(lod);
        return this;
    }

    @Override
    ImplicitGeometryProperty self() {
        return this;
    }
}
