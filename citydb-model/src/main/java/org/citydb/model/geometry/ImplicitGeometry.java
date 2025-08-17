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

import org.citydb.model.common.*;
import org.citydb.model.property.AppearanceProperty;
import org.citydb.model.util.AffineTransformer;
import org.citydb.model.util.matrix.Matrix;

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

    public Envelope getEnvelope(Matrix4x4 transformationMatrix, Point referencePoint) {
        if (transformationMatrix != null && referencePoint != null) {
            Envelope envelope;
            if (geometry != null) {
                envelope = geometry.getEnvelope();
                AffineTransformer.of(transformationMatrix.plus(new Matrix(4, 4)
                                .set(0, 3, referencePoint.getCoordinate().getX())
                                .set(1, 3, referencePoint.getCoordinate().getY())
                                .set(2, 3, referencePoint.getCoordinate().getZ())))
                        .transform(envelope);
            } else {
                envelope = Envelope.empty().include(Point.of(Coordinate.of(
                        referencePoint.getCoordinate().getX() + transformationMatrix.get(0, 3),
                        referencePoint.getCoordinate().getY() + transformationMatrix.get(1, 3),
                        referencePoint.getCoordinate().getZ() + transformationMatrix.get(2, 3))));
            }

            return envelope.setSRID(referencePoint.getSRID().orElse(null))
                    .setSrsIdentifier(referencePoint.getSrsIdentifier().orElse(null));
        } else {
            return null;
        }
    }

    @Override
    public void accept(Visitor visitor) {
        visitor.visit(this);
    }
}
