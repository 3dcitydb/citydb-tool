/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.operation.exporter.util;

import org.citydb.model.appearance.Appearance;
import org.citydb.model.appearance.SurfaceDataProperty;
import org.citydb.model.common.DatabaseDescriptor;
import org.citydb.model.common.Visitable;
import org.citydb.model.feature.Feature;
import org.citydb.model.property.Attribute;
import org.citydb.model.property.FeatureProperty;
import org.citydb.model.property.Property;
import org.citydb.model.util.AffineTransformer;
import org.citydb.model.util.GeometryInfo;
import org.citydb.model.walker.ModelWalker;
import org.citydb.operation.exporter.ExportHelper;

import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

public class Postprocessor {
    private final ExportHelper helper;
    private final EnvelopeHelper envelopeHelper;
    private final AppearanceHelper appearanceHelper;
    private final AffineTransformer transformer;
    private final Comparator<Property<?>> comparator = Comparator.comparingLong(
            property -> property.getDescriptor()
                    .map(DatabaseDescriptor::getId)
                    .orElse(0L));

    public Postprocessor(ExportHelper helper) {
        this.helper = helper;
        appearanceHelper = new AppearanceHelper(helper);
        envelopeHelper = new EnvelopeHelper(helper);
        transformer = helper.getOptions().getAffineTransform().map(AffineTransformer::of).orElse(null);
    }

    public void process(Feature feature) {
        appearanceHelper.assignSurfaceData(feature, helper.getSurfaceDataMapper());

        if (helper.getLodFilter().hasRemovedGeometry()) {
            Set<String> featureIds = removeEmptyFeatures(feature);
            Set<String> surfaceDataIds = appearanceHelper.removeEmptySurfaceData(feature);

            if (!featureIds.isEmpty() || !surfaceDataIds.isEmpty()) {
                processReferences(feature, featureIds, surfaceDataIds);
            }

            if (!surfaceDataIds.isEmpty()) {
                appearanceHelper.removeEmptyAppearances(feature);
            }

            envelopeHelper.updateEnvelope(feature);
        }

        if (transformer != null) {
            transformer.transform(feature);
        }

        sortAttributes(feature);
    }

    public void process(Visitable visitable) {
        appearanceHelper.assignSurfaceData(visitable, helper.getSurfaceDataMapper());

        if (transformer != null) {
            transformer.transform(visitable);
        }

        sortAttributes(visitable);
    }

    private Set<String> removeEmptyFeatures(Feature feature) {
        Set<String> featureIds = new HashSet<>();
        feature.accept(new ModelWalker() {
            @Override
            public void visit(FeatureProperty property) {
                Feature child = property.getObject().orElse(null);
                if (child != null && hasEmptyGeometry(child)) {
                    property.removeFromParent();
                    child.getObjectId().ifPresent(featureIds::add);
                } else {
                    super.visit(property);
                }
            }
        });

        return featureIds;
    }

    private boolean hasEmptyGeometry(Feature feature) {
        GeometryInfo geometryInfo = feature.getGeometryInfo(GeometryInfo.Mode.INCLUDE_CONTAINED_FEATURES);
        return !geometryInfo.hasGeometries()
                && !geometryInfo.hasImplicitGeometries();
    }

    private void processReferences(Feature feature, Set<String> featureIds, Set<String> surfaceDataIds) {
        feature.accept(new ModelWalker() {
            @Override
            public void visit(FeatureProperty property) {
                property.getReference()
                        .filter(featureIds::contains)
                        .ifPresent(reference -> property.removeFromParent());
                super.visit(property);
            }

            @Override
            public void visit(Appearance appearance) {
                Iterator<SurfaceDataProperty> iterator = appearance.getSurfaceData().iterator();
                while (iterator.hasNext()) {
                    iterator.next().getReference()
                            .filter(surfaceDataIds::contains)
                            .ifPresent(reference -> iterator.remove());
                }
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
