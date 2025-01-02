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

package org.citydb.operation.exporter.util;

import org.citydb.model.common.Matrix4x4;
import org.citydb.model.common.Reference;
import org.citydb.model.common.RelationType;
import org.citydb.model.feature.Feature;
import org.citydb.model.geometry.Coordinate;
import org.citydb.model.geometry.Envelope;
import org.citydb.model.geometry.ImplicitGeometry;
import org.citydb.model.geometry.Point;
import org.citydb.model.property.FeatureProperty;
import org.citydb.model.property.ImplicitGeometryProperty;
import org.citydb.model.util.GeometryInfo;
import org.citydb.model.walker.ModelWalker;
import org.citydb.operation.exporter.ExportHelper;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

public class EnvelopeHelper {
    private final ExportHelper helper;

    EnvelopeHelper(ExportHelper helper) {
        this.helper = helper;
    }

    public void updateEnvelope(Feature feature) {
        Map<String, ImplicitGeometry> implicitGeometries = new HashMap<>();
        feature.accept(new ModelWalker() {
            @Override
            public void visit(ImplicitGeometry implicitGeometry) {
                implicitGeometry.getObjectId().ifPresent(objectId ->
                        implicitGeometries.put(objectId, implicitGeometry));
            }
        });

        updateEnvelope(feature, implicitGeometries);
    }

    private void updateEnvelope(Feature feature, Map<String, ImplicitGeometry> implicitGeometries) {
        Deque<Envelope> envelopes = new ArrayDeque<>();
        feature.accept(new ModelWalker() {
            @Override
            public void visit(Feature feature) {
                envelopes.push(computeEnvelope(feature, implicitGeometries));
                super.visit(feature);

                Envelope envelope = envelopes.pop();
                if (!envelope.isEmpty()) {
                    feature.setEnvelope(envelope);
                    if (!envelopes.isEmpty()) {
                        envelopes.peek().include(envelope);
                    }
                }
            }

            @Override
            public void visit(FeatureProperty property) {
                if (property.getRelationType() == RelationType.CONTAINS) {
                    super.visit(property);
                }
            }
        });
    }

    private Envelope computeEnvelope(Feature feature, Map<String, ImplicitGeometry> implicitGeometries) {
        Envelope envelope = Envelope.empty();
        GeometryInfo geometryInfo = feature.getGeometryInfo(GeometryInfo.Mode.SKIP_NESTED_FEATURES);

        if (helper.getAdapter().getDatabaseMetadata().getSpatialReference().getSRID() == helper.getSRID()
                || !geometryInfo.hasImplicitGeometries()) {
            if (geometryInfo.hasGeometries()) {
                geometryInfo.getGeometries().stream()
                        .map(property -> property.getObject().getEnvelope())
                        .forEach(envelope::include);
            }

            if (geometryInfo.hasImplicitGeometries()) {
                for (ImplicitGeometryProperty property : geometryInfo.getImplicitGeometries()) {
                    Matrix4x4 transformationMatrix = property.getTransformationMatrix().orElse(null);
                    Point referencePoint = property.getReferencePoint().orElse(null);
                    if (transformationMatrix != null && referencePoint != null) {
                        ImplicitGeometry geometry = property.getObject().orElse(
                                implicitGeometries.get(property.getReference()
                                        .map(Reference::getTarget).orElse(null)));
                        if (geometry != null) {
                            envelope.include(geometry.getEnvelope(transformationMatrix, referencePoint));
                        } else {
                            envelope.include(Point.of(Coordinate.of(
                                    referencePoint.getCoordinate().getX() + transformationMatrix.get(0, 3),
                                    referencePoint.getCoordinate().getY() + transformationMatrix.get(1, 3),
                                    referencePoint.getCoordinate().getZ() + transformationMatrix.get(2, 3))));
                        }
                    }
                }
            }
        }

        return envelope;
    }
}
