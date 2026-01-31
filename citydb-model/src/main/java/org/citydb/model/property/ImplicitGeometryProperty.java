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

package org.citydb.model.property;

import org.citydb.model.common.Child;
import org.citydb.model.common.InlineOrByReferenceProperty;
import org.citydb.model.common.Matrix4x4;
import org.citydb.model.common.Name;
import org.citydb.model.feature.Feature;
import org.citydb.model.geometry.ImplicitGeometry;
import org.citydb.model.geometry.Point;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class ImplicitGeometryProperty extends Property<ImplicitGeometryProperty> implements InlineOrByReferenceProperty<ImplicitGeometry> {
    private ImplicitGeometry implicitGeometry;
    private String reference;
    private Matrix4x4 transformationMatrix;
    private Point referencePoint;
    private String lod;

    private ImplicitGeometryProperty(Name name, ImplicitGeometry implicitGeometry) {
        super(name, DataType.IMPLICIT_GEOMETRY_PROPERTY);
        Objects.requireNonNull(implicitGeometry, "The implicit geometry must not be null.");
        this.implicitGeometry = asChild(implicitGeometry);
    }

    private ImplicitGeometryProperty(Name name, String reference) {
        super(name, DataType.IMPLICIT_GEOMETRY_PROPERTY);
        Objects.requireNonNull(reference, "The reference must not be null.");
        this.reference = reference;
    }

    public static ImplicitGeometryProperty of(Name name, ImplicitGeometry implicitGeometry) {
        return new ImplicitGeometryProperty(name, implicitGeometry);
    }

    public static ImplicitGeometryProperty of(Name name, String reference) {
        return new ImplicitGeometryProperty(name, reference);
    }

    public static ImplicitGeometryProperty asReference(Name name, ImplicitGeometry implicitGeometry) {
        Objects.requireNonNull(implicitGeometry, "The referenced implicit geometry must not be null.");
        return new ImplicitGeometryProperty(name, implicitGeometry.getOrCreateObjectId());
    }

    @Override
    public Optional<ImplicitGeometry> getObject() {
        return Optional.ofNullable(implicitGeometry);
    }

    @Override
    public ImplicitGeometryProperty setObject(ImplicitGeometry implicitGeometry) {
        if (implicitGeometry != null) {
            this.implicitGeometry = asChild(implicitGeometry);
            reference = null;
        }

        return this;
    }

    @Override
    public Optional<String> getReference() {
        return Optional.ofNullable(reference);
    }

    @Override
    public ImplicitGeometryProperty setReference(String reference) {
        if (reference != null) {
            this.reference = reference;
            implicitGeometry = null;
        }

        return this;
    }

    @Override
    public ImplicitGeometryProperty setReference(ImplicitGeometry implicitGeometry) {
        return implicitGeometry != null ? setReference(implicitGeometry.getOrCreateObjectId()) : this;
    }

    public Optional<Matrix4x4> getTransformationMatrix() {
        return Optional.ofNullable(transformationMatrix);
    }

    public ImplicitGeometryProperty setTransformationMatrix(Matrix4x4 transformationMatrix) {
        this.transformationMatrix = transformationMatrix;
        return this;
    }

    public ImplicitGeometryProperty setTransformationMatrix(List<Double> transformationMatrix) {
        if (transformationMatrix != null && transformationMatrix.size() > 15) {
            this.transformationMatrix = Matrix4x4.ofRowMajor(transformationMatrix);
        }

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
    public boolean removeFromParent() {
        Child parent = getParent().orElse(null);
        if (parent instanceof Feature feature) {
            return feature.getImplicitGeometries().remove(this);
        } else if (parent instanceof Attribute attribute) {
            return attribute.getProperties().remove(this);
        } else {
            return false;
        }
    }

    @Override
    ImplicitGeometryProperty self() {
        return this;
    }
}
