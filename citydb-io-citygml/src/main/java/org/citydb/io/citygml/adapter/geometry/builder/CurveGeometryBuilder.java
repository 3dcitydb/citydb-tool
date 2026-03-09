/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
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
