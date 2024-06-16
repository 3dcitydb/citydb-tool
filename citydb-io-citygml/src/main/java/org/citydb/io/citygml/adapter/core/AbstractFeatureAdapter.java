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
            target.setEnvelope(Envelope.of(
                            Coordinate.of(coordinates.get(0), coordinates.get(1), coordinates.get(2)),
                            Coordinate.of(coordinates.get(3), coordinates.get(4), coordinates.get(5)))
                    .setSrsIdentifier(helper.getInheritedSrsName(source.getBoundedBy().getEnvelope())));
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
