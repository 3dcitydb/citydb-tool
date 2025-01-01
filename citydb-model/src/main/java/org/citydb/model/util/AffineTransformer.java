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

package org.citydb.model.util;

import org.citydb.model.appearance.GeoreferencedTexture;
import org.citydb.model.appearance.ParameterizedTexture;
import org.citydb.model.feature.Feature;
import org.citydb.model.geometry.*;
import org.citydb.model.property.AppearanceProperty;
import org.citydb.model.property.ImplicitGeometryProperty;
import org.citydb.model.util.matrix.Matrix;
import org.citydb.model.walker.ModelWalker;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class AffineTransformer {
    private final Matrix matrix;
    private final Matrix inverse;
    private final Processor processor = new Processor();

    private AffineTransformer(Matrix matrix) {
        this.matrix = matrix;
        inverse = matrix.invert();
    }

    public static AffineTransformer of(Matrix matrix) {
        Objects.requireNonNull(matrix, "The matrix must not be null.");
        int rows = Math.min(matrix.getRows(), 4);
        int columns = Math.min(matrix.getColumns(), 4);
        return new AffineTransformer(rows != 4 || columns != 4 ?
                Matrix.identity(4, 4).setSubMatrix(0, rows - 1, 0, columns - 1, matrix) :
                matrix.copy());
    }

    public static AffineTransformer ofRowMajor(List<Double> values, int rows) {
        Objects.requireNonNull(values, "The matrix values must not be null.");
        return of(new Matrix(values, rows));
    }

    public static AffineTransformer ofRowMajor(List<Double> values) {
        return ofRowMajor(values, 4);
    }

    public void transform(Coordinate coordinate) {
        Matrix transformed = matrix.times(new Matrix(new double[][]{
                {coordinate.getX()},
                {coordinate.getY()},
                {coordinate.getZ()},
                {1}
        }));

        coordinate.setX(transformed.get(0, 0))
                .setY(transformed.get(1, 0));
        if (coordinate.getDimension() == 3) {
            coordinate.setZ(transformed.get(2, 0));
        }
    }

    public void transform(Feature feature) {
        feature.accept(processor);
    }

    public void transform(Geometry<?> geometry) {
        geometry.accept(processor);
    }

    public void transform(Envelope envelope) {
        if (!envelope.isEmpty()) {
            transform(envelope.getLowerCorner());
            transform(envelope.getUpperCorner());
        }
    }

    private class Processor extends ModelWalker {
        @Override
        public void visit(Feature feature) {
            feature.getEnvelope().ifPresent(AffineTransformer.this::transform);
            super.visit(feature);
        }

        @Override
        public void visit(GeoreferencedTexture texture) {
            texture.getOrientation().ifPresent(orientation -> texture.setOrientation(
                    new Matrix(orientation, 2)
                            .times(inverse.getSubMatrix(0, 1, 0, 1))
                            .getRowMajor()
                            .subList(0, 4)));
            super.visit(texture);
        }

        @Override
        public void visit(ParameterizedTexture texture) {
            if (texture.hasWorldToTextureMappings()) {
                Map<Surface<?>, List<Double>> mappings = new IdentityHashMap<>(texture.getWorldToTextureMappings());
                mappings.forEach((surface, worldToTexture) -> texture.addWorldToTextureMapping(surface,
                        Matrix.identity(4, 4).setSubMatrix(0, 2, 0, 3, new Matrix(worldToTexture, 3))
                                .times(inverse)
                                .getRowMajor()
                                .subList(0, 12)));
            }
        }

        @Override
        public void visit(ImplicitGeometryProperty property) {
            property.getTransformationMatrix().ifPresent(transformationMatrix -> property.setTransformationMatrix(
                    matrix.copy()
                            .set(0, 3, 0)
                            .set(1, 3, 0)
                            .set(2, 3, 0)
                            .times(new Matrix(transformationMatrix, 4))
                            .getRowMajor()));
            super.visit(property);
        }

        @Override
        public void visit(ImplicitGeometry implicitGeometry) {
            if (implicitGeometry.hasAppearances()) {
                for (AppearanceProperty property : implicitGeometry.getAppearances().getAll()) {
                    visit(property);
                }
            }
        }

        @Override
        public void visit(Point point) {
            AffineTransformer.this.transform(point.getCoordinate());
        }

        @Override
        public void visit(LineString lineString) {
            lineString.getPoints().forEach(AffineTransformer.this::transform);
        }

        @Override
        public void visit(Polygon polygon) {
            transform(polygon.getExteriorRing());
            if (polygon.hasInteriorRings()) {
                polygon.getInteriorRings().forEach(this::transform);
            }
        }

        private void transform(LinearRing ring) {
            ring.getPoints().forEach(AffineTransformer.this::transform);
        }
    }
}
