/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.operation.exporter.util;

import org.citydb.model.feature.Feature;
import org.citydb.model.geometry.ImplicitGeometry;
import org.citydb.model.property.FeatureProperty;
import org.citydb.model.property.Property;
import org.citydb.model.util.GeometryInfo;
import org.citydb.model.walker.ModelWalker;
import org.citydb.operation.exporter.options.LodMode;
import org.citydb.operation.exporter.options.LodOptions;

import java.util.*;

public class LodFilter {
    private final Set<String> lods;
    private final LodMode mode;
    private final boolean isEnabled;
    private boolean hasRemovedGeometry;

    public LodFilter(LodOptions options) {
        Objects.requireNonNull(options, "The LoD filter options must not be null.");
        lods = options.getLods();
        mode = options.getMode();
        isEnabled = switch (mode) {
            case KEEP, REMOVE -> !lods.isEmpty();
            case MINIMUM, MAXIMUM -> true;
        };
    }

    public boolean isEnabled() {
        return isEnabled;
    }

    boolean hasRemovedGeometry() {
        return hasRemovedGeometry;
    }

    public boolean filter(String lod) {
        if (isEnabled && lod != null) {
            if (switch (mode) {
                case KEEP -> lods.contains(lod);
                case REMOVE -> !lods.contains(lod);
                case MINIMUM, MAXIMUM -> lods.isEmpty() || lods.contains(lod);
            }) {
                return true;
            } else {
                hasRemovedGeometry = true;
                return false;
            }
        } else {
            return true;
        }
    }

    Map<String, ImplicitGeometry> removeGeometries(Feature feature) {
        if (isEnabled && (mode == LodMode.MINIMUM || mode == LodMode.MAXIMUM)) {
            Map<String, ImplicitGeometry> implicitGeometries = new HashMap<>();
            GeometryInfo geometryInfo = feature.getGeometryInfo(GeometryInfo.Mode.INCLUDE_CONTAINED_FEATURES);
            String targetLod = geometryInfo.getLods().stream()
                    .min(mode == LodMode.MINIMUM ? Comparator.naturalOrder() : Comparator.reverseOrder())
                    .filter(lod -> lods.isEmpty() || lods.contains(lod))
                    .orElse("");

            if (geometryInfo.hasGeometries()) {
                geometryInfo.getGeometries().stream()
                        .filter(property -> !targetLod.equals(property.getLod().orElse(targetLod)))
                        .forEach(this::removeGeometryProperty);
            }

            if (geometryInfo.hasImplicitGeometries()) {
                geometryInfo.getImplicitGeometries().stream()
                        .filter(property -> !targetLod.equals(property.getLod().orElse(targetLod)))
                        .forEach(property -> {
                            removeGeometryProperty(property);
                            property.getObject().ifPresent(implicitGeometry ->
                                    implicitGeometry.getObjectId().ifPresent(objectId ->
                                            implicitGeometries.put(objectId, implicitGeometry)));
                        });
            }

            return implicitGeometries;
        } else {
            return Collections.emptyMap();
        }
    }

    Set<String> removeEmptyFeatures(Feature feature) {
        if (hasRemovedGeometry) {
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
        } else {
            return Collections.emptySet();
        }
    }

    private boolean hasEmptyGeometry(Feature feature) {
        GeometryInfo geometryInfo = feature.getGeometryInfo(GeometryInfo.Mode.INCLUDE_CONTAINED_FEATURES);
        return !geometryInfo.hasGeometries()
                && !geometryInfo.hasImplicitGeometries();
    }

    private void removeGeometryProperty(Property<?> property) {
        hasRemovedGeometry = true;
        property.removeFromParent();
    }
}
