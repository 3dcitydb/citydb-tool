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

import org.citydb.model.appearance.Appearance;
import org.citydb.model.appearance.SurfaceDataProperty;
import org.citydb.model.common.DatabaseDescriptor;
import org.citydb.model.common.Visitable;
import org.citydb.model.feature.Feature;
import org.citydb.model.geometry.ImplicitGeometry;
import org.citydb.model.property.Attribute;
import org.citydb.model.property.FeatureProperty;
import org.citydb.model.property.ImplicitGeometryProperty;
import org.citydb.model.property.Property;
import org.citydb.model.walker.ModelWalker;
import org.citydb.operation.exporter.ExportHelper;

import java.util.Comparator;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

public class Postprocessor {
    private final ExportHelper helper;
    private final EnvelopeHelper envelopeHelper;
    private final AppearanceHelper appearanceHelper;
    private final Comparator<Property<?>> comparator = Comparator.comparingLong(
            property -> property.getDescriptor()
                    .map(DatabaseDescriptor::getId)
                    .orElse(0L));

    public Postprocessor(ExportHelper helper) {
        this.helper = helper;
        appearanceHelper = new AppearanceHelper(helper);
        envelopeHelper = new EnvelopeHelper(helper);
    }

    public void process(Feature feature) {
        Map<String, ImplicitGeometry> implicitGeometries = helper.getLodFilter().removeGeometries(feature);
        appearanceHelper.assignSurfaceData(feature, helper.getSurfaceDataMapper());

        if (helper.getLodFilter().hasRemovedGeometry()) {
            Set<String> featureIds = helper.getLodFilter().removeEmptyFeatures(feature);
            Set<String> surfaceDataIds = appearanceHelper.removeEmptySurfaceData(feature);

            if (!featureIds.isEmpty()
                    || !surfaceDataIds.isEmpty()
                    || !implicitGeometries.isEmpty()) {
                processReferences(feature, featureIds, surfaceDataIds, implicitGeometries);
            }

            if (!surfaceDataIds.isEmpty()) {
                appearanceHelper.removeEmptyAppearances(feature);
            }

            envelopeHelper.updateEnvelope(feature);
        }

        sortAttributes(feature);
    }

    public void process(Visitable visitable) {
        appearanceHelper.assignSurfaceData(visitable, helper.getSurfaceDataMapper());
        sortAttributes(visitable);
    }

    private void processReferences(Feature feature, Set<String> featureIds, Set<String> surfaceDataIds, Map<String, ImplicitGeometry> implicitGeometries) {
        feature.accept(new ModelWalker() {
            @Override
            public void visit(FeatureProperty property) {
                property.getReference()
                        .filter(reference -> featureIds.contains(reference.getTarget()))
                        .ifPresent(reference -> property.removeFromParent());
                super.visit(property);
            }

            @Override
            public void visit(Appearance appearance) {
                Iterator<SurfaceDataProperty> iterator = appearance.getSurfaceData().iterator();
                while (iterator.hasNext()) {
                    iterator.next().getReference()
                            .filter(reference -> surfaceDataIds.contains(reference.getTarget()))
                            .ifPresent(reference -> iterator.remove());
                }
            }

            @Override
            public void visit(ImplicitGeometryProperty property) {
                property.getReference()
                        .map(reference -> implicitGeometries.remove(reference.getTarget()))
                        .ifPresent(property::setObject);
            }
        });
    }

    private void sortAttributes(Visitable visitable) {
        visitable.accept(new ModelWalker() {
            @Override
            public void visit(Feature feature) {
                super.visit(feature);
                if (feature.hasAttributes()) {
                    feature.getAttributes().sortPropertiesWithIdenticalNames(comparator);
                }

                if (feature.hasGeometries()) {
                    feature.getGeometries().sortPropertiesWithIdenticalNames(comparator);
                }

                if (feature.hasImplicitGeometries()) {
                    feature.getImplicitGeometries().sortPropertiesWithIdenticalNames(comparator);
                }

                if (feature.hasFeatures()) {
                    feature.getFeatures().sortPropertiesWithIdenticalNames(comparator);
                }

                if (feature.hasAppearances()) {
                    feature.getAppearances().sortPropertiesWithIdenticalNames(comparator);
                }
            }

            @Override
            public void visit(Attribute attribute) {
                super.visit(attribute);
                if (attribute.hasProperties()) {
                    attribute.getProperties().sortPropertiesWithIdenticalNames(comparator);
                }
            }
        });
    }
}
