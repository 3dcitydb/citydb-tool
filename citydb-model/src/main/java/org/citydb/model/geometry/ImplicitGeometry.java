/*
 * citydb-tool - Command-line tool for the 3D City Database
 * https://www.3dcitydb.org/
 *
 * Copyright 2022-2023
 * Virtual City Systems, Germany
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

import org.citydb.model.common.*;
import org.citydb.model.property.AppearanceProperty;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class ImplicitGeometry extends Child implements Referencable, Visitable {
    private Geometry<?> geometry;
    private ExternalFile libraryObject;
    private String objectId;
    private PropertyMap<AppearanceProperty> appearances;

    private ImplicitGeometry(Geometry<?> geometry) {
        Objects.requireNonNull(geometry, "The geometry must not be null.");
        setGeometry(geometry);
    }

    private ImplicitGeometry(ExternalFile libraryObject) {
        Objects.requireNonNull(libraryObject, "The library object must not be null.");
        setLibraryObject(libraryObject);
    }

    public static ImplicitGeometry of(Geometry<?> geometry) {
        return new ImplicitGeometry(geometry);
    }

    public static ImplicitGeometry of(ExternalFile libraryObjectFile) {
        return new ImplicitGeometry(libraryObjectFile);
    }

    public Optional<Geometry<?>> getGeometry() {
        return Optional.ofNullable(geometry);
    }

    public ImplicitGeometry setGeometry(Geometry<?> geometry) {
        if (geometry != null) {
            this.geometry = asChild(geometry);
            objectId = geometry.getObjectId().orElse(null);
            libraryObject = null;
        }

        return this;
    }

    public Optional<ExternalFile> getLibraryObject() {
        return Optional.ofNullable(libraryObject);
    }

    public ImplicitGeometry setLibraryObject(ExternalFile libraryObject) {
        if (libraryObject != null) {
            this.libraryObject = libraryObject;
            objectId = libraryObject.getObjectId().orElse(null);
            geometry = null;
        }

        return this;
    }

    @Override
    public Optional<String> getObjectId() {
        return Optional.ofNullable(objectId);
    }

    @Override
    public ImplicitGeometry setObjectId(String objectId) {
        this.objectId = objectId;
        return this;
    }

    public boolean hasAppearances() {
        return appearances != null && !appearances.isEmpty();
    }

    public PropertyMap<AppearanceProperty> getAppearances() {
        if (appearances == null) {
            appearances = new PropertyMap<>(this);
        }

        return appearances;
    }

    public ImplicitGeometry setAppearances(List<AppearanceProperty> appearances) {
        this.appearances = new PropertyMap<>(this, appearances);
        return this;
    }

    public ImplicitGeometry addAppearance(AppearanceProperty appearance) {
        if (appearance != null) {
            getAppearances().put(appearance);
        }

        return this;
    }

    public Envelope getEnvelope(List<Double> transformationMatrix, Point referencePoint) {
        if (geometry != null
                && transformationMatrix != null
                && transformationMatrix.size() > 15
                && referencePoint != null) {
            double[][] matrix = new double[4][4];
            for (int i = 0; i < 4; i++) {
                for (int j = 0; j < 4; j++) {
                    matrix[i][j] = transformationMatrix.get(i * 4 + j);
                }
            }

            matrix[0][3] += referencePoint.getCoordinate().getX();
            matrix[1][3] += referencePoint.getCoordinate().getY();
            matrix[2][3] += referencePoint.getCoordinate().getZ();

            Envelope template = geometry.getEnvelope();
            return Envelope.of(
                            multiply(matrix, template.getLowerCorner()),
                            multiply(matrix, template.getUpperCorner()))
                    .setSRID(referencePoint.getSRID().orElse(null))
                    .setSrsName(referencePoint.getSrsName().orElse(null));
        } else {
            return null;
        }
    }

    private Coordinate multiply(double[][] matrix, Coordinate coordinate) {
        double[] v = new double[]{coordinate.getX(), coordinate.getY(), coordinate.getZ(), 1};
        return Coordinate.of(
                matrix[0][0] * v[0] + matrix[0][1] * v[1] + matrix[0][2] * v[2] + matrix[0][3] * v[3],
                matrix[1][0] * v[0] + matrix[1][1] * v[1] + matrix[1][2] * v[2] + matrix[1][3] * v[3],
                matrix[2][0] * v[0] + matrix[2][1] * v[1] + matrix[2][2] * v[2] + matrix[2][3] * v[3]);
    }

    @Override
    public void accept(Visitor visitor) {
        visitor.visit(this);
    }
}
