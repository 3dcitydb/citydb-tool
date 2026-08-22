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
import org.citydb.model.property.ImplicitGeometryReference;
import org.citydb.model.property.RelationType;
import org.citydb.model.util.AffineTransformer;
import org.citydb.model.util.GeometryInfo;
import org.citydb.model.util.matrix.Matrix;
import org.citydb.model.walker.ModelWalker;
import org.citydb.operation.exporter.ExportHelper;

import java.util.ArrayDeque;
import java.util.Deque;

public class EnvelopeHelper {
    private final ExportHelper helper;
    private final ImplicitGeometryRegistry implicitGeometryRegistry;

    EnvelopeHelper(ExportHelper helper) {
        this.helper = helper;
        implicitGeometryRegistry = helper.getImplicitGeometryRegistry();
    }

    public void updateEnvelope(Feature feature) {
        Deque<Envelope> envelopes = new ArrayDeque<>();
        feature.accept(new ModelWalker() {
            @Override
            public void visit(Feature feature) {
                envelopes.push(computeEnvelope(feature));
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

    private Envelope computeEnvelope(Feature feature) {
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
                        ImplicitGeometry geometry = property.getObject().orElse(null);
                        if (geometry != null) {
                            envelope.include(geometry.getEnvelope(transformationMatrix, referencePoint));
                        } else {
                            Envelope extent = implicitGeometryRegistry.getEnvelope(property.getReference()
                                    .map(ImplicitGeometryReference::getObjectId)
                                    .orElse(null));
                            if (extent != null) {
                                AffineTransformer.of(transformationMatrix.plus(new Matrix(4, 4)
                                                .set(0, 3, referencePoint.getCoordinate().getX())
                                                .set(1, 3, referencePoint.getCoordinate().getY())
                                                .set(2, 3, referencePoint.getCoordinate().getZ())))
                                        .transform(extent);
                                envelope.include(extent);
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
        }

        return envelope;
    }
}
