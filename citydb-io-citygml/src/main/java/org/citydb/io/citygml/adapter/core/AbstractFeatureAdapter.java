/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.io.citygml.adapter.core;

import org.citydb.io.citygml.adapter.gml.AbstractGMLAdapter;
import org.citydb.io.citygml.builder.ModelBuildException;
import org.citydb.io.citygml.reader.ModelBuilderHelper;
import org.citydb.io.citygml.serializer.ModelSerializeException;
import org.citydb.io.citygml.writer.ModelSerializerHelper;
import org.citydb.model.feature.Feature;
import org.citydb.model.geometry.Coordinate;
import org.citydb.model.geometry.Envelope;
import org.citygml4j.core.model.ade.ADEProperty;
import org.citygml4j.core.model.core.AbstractFeature;
import org.citygml4j.core.model.core.ImplicitGeometry;
import org.citygml4j.core.visitor.ObjectWalker;
import org.xmlobjects.gml.model.feature.BoundingShape;
import org.xmlobjects.gml.model.geometry.DirectPosition;
import org.xmlobjects.gml.util.EnvelopeOptions;

import java.util.List;

public abstract class AbstractFeatureAdapter<T extends AbstractFeature> extends AbstractGMLAdapter<T> {

    @Override
    public void build(T source, Feature target, ModelBuilderHelper helper) throws ModelBuildException {
        super.build(source, target, helper);

        if (helper.isComputeEnvelopes()
                || source.getBoundedBy() == null
                || !source.getBoundedBy().isSetEnvelope()) {
            source.computeEnvelope(EnvelopeOptions.defaults().setEnvelopeOnFeatures(true));
        } else {
            // make sure implicit geometries are included in envelope
            source.accept(new ObjectWalker() {
                @Override
                public void visit(ImplicitGeometry implicitGeometry) {
                    source.getBoundedBy().getEnvelope().include(implicitGeometry.computeEnvelope());
                }
            });
        }

        if (source.getBoundedBy() != null
                && source.getBoundedBy().isSetEnvelope()) {
            List<Double> coordinates = source.getBoundedBy().getEnvelope().toCoordinateList3D();
            if (!coordinates.isEmpty()) {
                target.setEnvelope(Envelope.empty()
                        .include(coordinates.get(0), coordinates.get(1), coordinates.get(2))
                        .include(coordinates.get(3), coordinates.get(4), coordinates.get(5))
                        .setSrsIdentifier(helper.getInheritedSrsName(source.getBoundedBy().getEnvelope())));
            }
        }

        if (source.hasADEProperties()) {
            for (ADEProperty property : source.getADEProperties()) {
                helper.addAttribute(property, target);
            }
        }
    }

    @Override
    public void serialize(Feature source, T target, ModelSerializerHelper helper) throws ModelSerializeException {
        super.serialize(source, target, helper);
        boolean isTopLevel = source.getParent().isEmpty();

        if (isTopLevel) {
            source.getEnvelope().ifPresent(value -> {
                Coordinate lowerCorner = value.getLowerCorner();
                Coordinate upperCorner = value.getUpperCorner();
                org.xmlobjects.gml.model.geometry.Envelope envelope = new org.xmlobjects.gml.model.geometry.Envelope(
                        new DirectPosition(lowerCorner.getX(), lowerCorner.getY(), lowerCorner.getZ()),
                        new DirectPosition(upperCorner.getX(), upperCorner.getY(), upperCorner.getZ()));
                envelope.setSrsDimension(3);
                envelope.setSrsName(helper.getSrsName());
                target.setBoundedBy(new BoundingShape(envelope));
            });
        }
    }
}
