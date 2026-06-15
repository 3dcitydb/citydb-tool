/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.io.citygml.reader.preprocess;

import org.citydb.core.exception.UncheckedException;
import org.citydb.core.function.CheckedFunction;
import org.citydb.io.citygml.builder.ModelBuildException;
import org.citydb.io.citygml.reader.util.FeatureHelper;
import org.citygml4j.core.model.core.AbstractAppearanceProperty;
import org.citygml4j.core.model.core.AbstractFeature;
import org.citygml4j.core.model.core.ImplicitGeometry;
import org.citygml4j.core.visitor.ObjectWalker;
import org.xmlobjects.gml.model.geometry.AbstractGeometry;
import org.xmlobjects.gml.model.geometry.Envelope;
import org.xmlobjects.gml.model.geometry.GeometryProperty;
import org.xmlobjects.gml.util.Matrices;
import org.xmlobjects.gml.util.matrix.Matrix;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ImplicitGeometryResolver {
    private final Map<String, ImplicitGeometry> implicitGeometries = new ConcurrentHashMap<>();
    private final Map<String, org.citydb.model.geometry.ImplicitGeometry> converted = new ConcurrentHashMap<>();
    private final Map<String, Envelope> envelopes = new ConcurrentHashMap<>();

    ImplicitGeometryResolver() {
    }

    public boolean hasImplicitGeometries() {
        return !implicitGeometries.isEmpty();
    }

    Collection<ImplicitGeometry> getImplicitGeometries() {
        return implicitGeometries.values();
    }

    public org.citydb.model.geometry.ImplicitGeometry getOrConvert(String objectId, Converter converter) throws ModelBuildException {
        try {
            return converted.computeIfAbsent(objectId, k -> {
                try {
                    ImplicitGeometry implicitGeometry = implicitGeometries.remove(k);
                    return implicitGeometry != null
                            ? converter.apply(implicitGeometry)
                            : null;
                } catch (Exception e) {
                    throw UncheckedException.wrap(e);
                }
            });
        } catch (UncheckedException e) {
            throw UncheckedException.unwrap(e, ModelBuildException.class);
        }
    }

    public Envelope computeEnvelope(ImplicitGeometry implicitGeometry) {
        Envelope envelope = new Envelope();
        if (implicitGeometry.getRelativeGeometry() != null
                && implicitGeometry.getRelativeGeometry().getHref() != null) {
            String objectId = FeatureHelper.getIdFromReference(implicitGeometry.getRelativeGeometry().getHref());
            Envelope relative = envelopes.get(objectId);

            if (relative != null
                    && implicitGeometry.getTransformationMatrix() != null
                    && implicitGeometry.getReferencePoint() != null
                    && implicitGeometry.getReferencePoint().getObject() != null) {
                Matrix matrix = implicitGeometry.getTransformationMatrix().getValue().copy();

                List<Double> point = implicitGeometry.getReferencePoint().getObject().toCoordinateList3D();
                if (!point.isEmpty()) {
                    matrix.set(0, 3, matrix.get(0, 3) + point.get(0));
                    matrix.set(1, 3, matrix.get(1, 3) + point.get(1));
                    matrix.set(2, 3, matrix.get(2, 3) + point.get(2));
                }

                envelope.include(Matrices.transform3D(relative.getLowerCorner(), matrix));
                envelope.include(Matrices.transform3D(relative.getUpperCorner(), matrix));
            } else if (implicitGeometry.getReferencePoint() != null
                    && implicitGeometry.getReferencePoint().getObject() != null) {
                List<Double> point = implicitGeometry.getReferencePoint().getObject().toCoordinateList3D();
                if (!point.isEmpty()) {
                    if (implicitGeometry.getTransformationMatrix() != null) {
                        Matrix matrix = implicitGeometry.getTransformationMatrix().getValue();
                        point.set(0, point.get(0) + matrix.get(0, 3));
                        point.set(1, point.get(1) + matrix.get(1, 3));
                        point.set(2, point.get(2) + matrix.get(2, 3));
                    }

                    envelope.include(point);
                }
            }
        }

        return envelope;
    }

    void collectImplicitGeometries(AbstractFeature feature) {
        feature.accept(new ObjectWalker() {
            @Override
            public void visit(ImplicitGeometry implicitGeometry) {
                if (implicitGeometry.getRelativeGeometry() != null
                        && implicitGeometry.getRelativeGeometry().isSetInlineObject()
                        && implicitGeometry.getRelativeGeometry().getObject().getId() != null) {
                    AbstractGeometry geometry = implicitGeometry.getRelativeGeometry().getObject();
                    ImplicitGeometry template = new ImplicitGeometry(new GeometryProperty<>(geometry));
                    if (implicitGeometry.isSetAppearances()) {
                        implicitGeometry.getAppearances().stream()
                                .filter(AbstractAppearanceProperty::isSetInlineObject)
                                .forEach(template.getAppearances()::add);
                    }

                    implicitGeometries.put(geometry.getId(), template);
                    envelopes.put(geometry.getId(), geometry.computeEnvelope());
                }
            }
        });
    }

    void removeTemplateGeometries(AbstractFeature feature) {
        feature.accept(new ObjectWalker() {
            @Override
            public void visit(ImplicitGeometry implicitGeometry) {
                if (implicitGeometry.getRelativeGeometry() != null
                        && implicitGeometry.getRelativeGeometry().isSetInlineObject()) {
                    String objectId = FeatureHelper.getOrCreateId(implicitGeometry.getRelativeGeometry().getObject());
                    implicitGeometry.getRelativeGeometry().setHref("#" + objectId);
                    implicitGeometry.getRelativeGeometry().setInlineObject(null);
                    implicitGeometry.getRelativeGeometry().setReferencedObject(null);
                }
            }
        });
    }

    @FunctionalInterface
    public interface Converter extends CheckedFunction<ImplicitGeometry, org.citydb.model.geometry.ImplicitGeometry, ModelBuildException> {
    }
}
