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
import org.citydb.model.geometry.LineString;
import org.citydb.model.geometry.MultiLineString;
import org.xmlobjects.gml.model.basictypes.Sign;
import org.xmlobjects.gml.model.geometry.AbstractGeometry;
import org.xmlobjects.gml.model.geometry.aggregates.MultiCurve;
import org.xmlobjects.gml.model.geometry.primitives.AbstractCurve;
import org.xmlobjects.gml.model.geometry.primitives.OrientableCurve;
import org.xmlobjects.gml.visitor.GeometryWalker;

import java.util.ArrayList;
import java.util.List;

public class CurveGeometryBuilder extends GeometryBuilder {
    private final CurveProcessor processor = new CurveProcessor();

    CurveGeometryBuilder() {
    }

    @Override
    Geometry<?> build(AbstractGeometry source) {
        try {
            source.accept(processor);

            if (!processor.lineStrings.isEmpty()) {
                if (source instanceof MultiCurve) {
                    return MultiLineString.of(processor.lineStrings);
                } else {
                    return processor.lineStrings.size() == 1 ?
                            processor.lineStrings.get(0) :
                            MultiLineString.of(processor.lineStrings);
                }
            } else {
                return null;
            }
        } finally {
            processor.lineStrings.clear();
            processor.isReverse = false;
        }
    }

    private static class CurveProcessor extends GeometryWalker {
        private final List<LineString> lineStrings = new ArrayList<>();
        private boolean isReverse;

        @Override
        public void visit(AbstractCurve curve) {
            lineStrings.add(LineString.of(Coordinate.of(curve.toCoordinateList3D(isReverse), 3))
                    .setObjectId(curve.getId()));
        }

        @Override
        public void visit(OrientableCurve orientableCurve) {
            if (orientableCurve.getOrientation() == Sign.MINUS) {
                isReverse = !isReverse;
                super.visit(orientableCurve);
                isReverse = !isReverse;
            } else {
                super.visit(orientableCurve);
            }
        }
    }
}
