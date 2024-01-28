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

package org.citydb.io.citygml.adapter.geometry.builder;

import org.citydb.io.citygml.adapter.appearance.builder.AppearanceHelper;
import org.citydb.io.citygml.builder.ModelBuildException;
import org.citydb.model.geometry.LinearRing;
import org.citydb.model.geometry.Polygon;
import org.citydb.model.geometry.Surface;
import org.citydb.model.geometry.TriangulatedSurface;
import org.citydb.model.geometry.*;
import org.xmlobjects.gml.model.GMLObject;
import org.xmlobjects.gml.model.base.AbstractGML;
import org.xmlobjects.gml.model.basictypes.Sign;
import org.xmlobjects.gml.model.common.CoordinateListProvider;
import org.xmlobjects.gml.model.geometry.AbstractGeometry;
import org.xmlobjects.gml.model.geometry.GeometryProperty;
import org.xmlobjects.gml.model.geometry.compact.AbstractSimplePolygon;
import org.xmlobjects.gml.model.geometry.primitives.*;
import org.xmlobjects.gml.visitor.GeometryWalker;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class SurfaceGeometryBuilder extends GeometryBuilder {
    private final AppearanceHelper appearanceHelper;
    private final SurfaceProcessor processor = new SurfaceProcessor();

    SurfaceGeometryBuilder(AppearanceHelper appearanceHelper) {
        this.appearanceHelper = appearanceHelper;
    }

    @Override
    Geometry<?> build(AbstractGeometry source) throws ModelBuildException {
        try {
            source.accept(processor);

            Surface<?> surface = null;
            if (!processor.polygons.isEmpty()) {
                if (source instanceof org.xmlobjects.gml.model.geometry.aggregates.MultiSurface) {
                    surface = MultiSurface.of(processor.polygons);
                } else if (source instanceof org.xmlobjects.gml.model.geometry.complexes.CompositeSurface
                        || source instanceof PolyhedralSurface
                        || source instanceof Shell) {
                    surface = CompositeSurface.of(processor.polygons);
                } else if (source instanceof org.xmlobjects.gml.model.geometry.primitives.TriangulatedSurface) {
                    surface = TriangulatedSurface.of(processor.polygons);
                } else if (source instanceof AbstractSurface) {
                    surface = processor.polygons.size() == 1 ?
                            processor.polygons.get(0) :
                            CompositeSurface.of(processor.polygons);
                } else {
                    surface = MultiSurface.of(processor.polygons);
                }

                surface.setObjectId(source.getId());
                appearanceHelper.addTarget(surface, source, null);
            }

            return surface;
        } finally {
            processor.polygons.clear();
            processor.isReverse = false;
        }
    }

    private class SurfaceProcessor extends GeometryWalker {
        private final List<Polygon> polygons = new ArrayList<>();
        private GeometryProperty<?> context;
        private boolean isReverse;

        @Override
        public void visit(org.xmlobjects.gml.model.geometry.primitives.Polygon polygon) {
            addPolygon(createPolygon(polygon.getExterior(),
                    polygon.isSetInterior() ? polygon.getInterior() : null), polygon);
        }

        @Override
        public void visit(AbstractSimplePolygon simplePolygon) {
            Polygon polygon = createPolygon(simplePolygon.getControlPoints());
            if (polygon != null) {
                appearanceHelper.addTarget(polygon.getExteriorRing(), simplePolygon, context);
                addPolygon(polygon, simplePolygon);
            }
        }

        @Override
        public void visit(PolygonPatch polygonPatch) {
            addPolygon(createPolygon(polygonPatch.getExterior(),
                    polygonPatch.isSetInterior() ? polygonPatch.getInterior() : null), polygonPatch);
        }

        @Override
        public void visit(Rectangle rectangle) {
            addPolygon(createPolygon(rectangle.getExterior(), null), rectangle);
        }

        @Override
        public void visit(Triangle triangle) {
            addPolygon(createPolygon(triangle.getExterior(), null), triangle);
        }

        @Override
        public void visit(OrientableSurface orientableSurface) {
            if (orientableSurface.getOrientation() == Sign.MINUS) {
                isReverse = !isReverse;
                super.visit(orientableSurface);
                isReverse = !isReverse;
            } else {
                super.visit(orientableSurface);
            }
        }

        @Override
        public void visit(GeometryProperty<?> property) {
            context = property;
            super.visit(property);
        }

        private void addPolygon(Polygon polygon, GMLObject source) {
            if (polygon != null) {
                if (source instanceof AbstractGML object) {
                    polygon.setObjectId(object.getId());
                }

                appearanceHelper.addTarget(polygon, source, context);
                polygons.add(polygon);
            }
        }

        private Polygon createPolygon(AbstractRingProperty exterior, List<AbstractRingProperty> interior) {
            LinearRing exteriorRing = createLinearRing(exterior);
            if (exteriorRing != null) {
                List<LinearRing> interiorRings = interior != null && !interior.isEmpty() ?
                        interior.stream()
                                .map(this::createLinearRing)
                                .filter(Objects::nonNull)
                                .collect(Collectors.toList()) :
                        null;

                return createPolygon(exteriorRing, interiorRings);
            } else {
                return null;
            }
        }

        private Polygon createPolygon(CoordinateListProvider provider) {
            return provider != null ? createPolygon(createLinearRing(provider), null) : null;
        }

        private Polygon createPolygon(LinearRing exteriorRing, List<LinearRing> interiorRings) {
            return exteriorRing != null ? Polygon.of(exteriorRing, interiorRings, isReverse) : null;
        }

        private LinearRing createLinearRing(AbstractRingProperty property) {
            if (property != null && property.getObject() != null) {
                LinearRing ring = createLinearRing(property.getObject()).setObjectId(property.getObject().getId());
                appearanceHelper.addTarget(ring, property.getObject(), context);
                return ring;
            } else {
                return null;
            }
        }

        private LinearRing createLinearRing(CoordinateListProvider provider) {
            return LinearRing.of(Coordinate.of(provider.toCoordinateList3D(isReverse), 3));
        }
    }
}
