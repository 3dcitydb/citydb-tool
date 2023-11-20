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

package org.citydb.io.citygml.adapter.geometry.builder;

import org.citydb.model.geometry.Coordinate;
import org.citydb.model.geometry.Geometry;
import org.citydb.model.geometry.MultiPoint;
import org.citydb.model.geometry.Point;
import org.citygml4j.core.visitor.ObjectWalker;
import org.xmlobjects.gml.model.common.CoordinateListProvider;
import org.xmlobjects.gml.model.geometry.AbstractGeometry;
import org.xmlobjects.gml.model.geometry.compact.SimpleMultiPoint;

import java.util.ArrayList;
import java.util.List;

public class PointGeometryBuilder extends GeometryBuilder {
    private final PointProcessor processor = new PointProcessor();

    PointGeometryBuilder() {
    }

    @Override
    Geometry<?> build(AbstractGeometry source) {
        try {
            source.accept(processor);

            if (!processor.points.isEmpty()) {
                if (source instanceof org.xmlobjects.gml.model.geometry.aggregates.MultiPoint
                        || source instanceof SimpleMultiPoint) {
                    return MultiPoint.of(processor.points);
                } else {
                    return processor.points.size() == 1 ?
                            processor.points.get(0) :
                            MultiPoint.of(processor.points);
                }
            } else {
                return null;
            }
        } finally {
            processor.points.clear();
        }
    }

    private static class PointProcessor extends ObjectWalker {
        private final List<Point> points = new ArrayList<>();

        @Override
        public void visit(org.xmlobjects.gml.model.geometry.primitives.Point point) {
            addPoint(point, point.getId());
        }

        @Override
        public void visit(SimpleMultiPoint simpleMultiPoint) {
            addPoint(simpleMultiPoint.getPosList(), null);
        }

        private void addPoint(CoordinateListProvider provider, String id) {
            if (provider != null) {
                List<Double> coordinates = provider.toCoordinateList3D();
                for (int i = 0; i < coordinates.size(); i += 3) {
                    points.add(Point.of(Coordinate.of(coordinates.get(i),
                                    coordinates.get(i + 1),
                                    coordinates.get(i + 2)))
                            .setObjectId(id));
                }
            }
        }
    }
}
