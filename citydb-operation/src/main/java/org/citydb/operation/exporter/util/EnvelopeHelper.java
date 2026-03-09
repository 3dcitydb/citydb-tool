/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.operation.exporter.util;

import org.citydb.model.common.Matrix4x4;
import org.citydb.model.feature.Feature;
import org.citydb.model.geometry.Coordinate;
import org.citydb.model.geometry.Envelope;
import org.citydb.model.geometry.ImplicitGeometry;
import org.citydb.model.geometry.Point;
import org.citydb.model.property.FeatureProperty;
import org.citydb.model.property.ImplicitGeometryProperty;
import org.citydb.model.property.RelationType;
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
                                implicitGeometries.get(property.getReference().orElse(null)));
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
