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
