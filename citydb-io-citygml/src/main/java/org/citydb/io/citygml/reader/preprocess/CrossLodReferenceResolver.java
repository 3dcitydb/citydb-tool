/*
 * citydb-tool - Command-line tool for the 3D City Database
 * https://www.3dcitydb.org/
 *
 * Copyright 2022-2023
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

package org.citydb.io.citygml.reader.preprocess;

import org.citygml4j.core.model.common.GeometryInfo;
import org.citygml4j.core.model.core.AbstractFeature;
import org.citygml4j.core.model.core.AbstractSpaceBoundary;
import org.citygml4j.core.visitor.ObjectWalker;
import org.xmlobjects.gml.model.GMLObject;
import org.xmlobjects.gml.model.geometry.AbstractGeometry;
import org.xmlobjects.gml.model.geometry.GeometryProperty;
import org.xmlobjects.gml.util.reference.ReferenceResolver;
import org.xmlobjects.model.Child;
import org.xmlobjects.util.copy.CopyBuilder;

import java.util.*;
import java.util.function.Supplier;

public class CrossLodReferenceResolver {
    private final Supplier<CopyBuilder> copyBuilderSupplier;
    private Mode mode = Mode.RESOLVE;
    private boolean copyAppearance = true;

    public enum Mode {
        RESOLVE,
        REMOVE_LOD4_REFERENCES
    }

    CrossLodReferenceResolver(Supplier<CopyBuilder> copyBuilderSupplier) {
        this.copyBuilderSupplier = copyBuilderSupplier;
    }

    CrossLodReferenceResolver setMode(Mode mode) {
        this.mode = mode;
        return this;
    }

    CrossLodReferenceResolver copyAppearance(boolean copyAppearance) {
        this.copyAppearance = copyAppearance;
        return this;
    }

    void resolveCrossLodReferences(AbstractFeature feature, ReferenceResolver referenceResolver) {
        referenceResolver.resolveReferences(feature);
        resolveCrossLodReferences(feature);
    }

    void resolveCrossLodReferences(AbstractFeature feature) {
        CrossLodReferenceCollector lodReferenceCollector = new CrossLodReferenceCollector();
        Map<AbstractGeometry, List<GeometryProperty<?>>> references = lodReferenceCollector.process(feature);
        if (!references.isEmpty()) {
            if (mode == Mode.RESOLVE) {
                GeometryCopyBuilder copyBuilder = new GeometryCopyBuilder(copyBuilderSupplier)
                        .copyAppearance(copyAppearance)
                        .withAppearanceHelper(new AppearanceHelper(feature));
                for (Map.Entry<AbstractGeometry, List<GeometryProperty<?>>> entry : references.entrySet()) {
                    GeometryProperty<?> targetProperty = getTargetProperty(entry.getValue());
                    AbstractGeometry geometry = copyBuilder.copy(entry.getKey(), feature);

                    targetProperty.setInlineObjectIfValid(geometry);
                    targetProperty.setHref(null);

                    if (entry.getValue().size() > 1) {
                        entry.getValue().stream()
                                .filter(property -> property != targetProperty)
                                .forEach(property -> property.setHref("#" + geometry.getId()));
                    }
                }
            } else {
                references.values().stream()
                        .flatMap(Collection::stream)
                        .forEach(property -> {
                            Child parent = property.getParent();
                            if (parent instanceof GMLObject object) {
                                object.unsetProperty(property);
                            }
                        });
            }
        }
    }

    private GeometryProperty<?> getTargetProperty(List<GeometryProperty<?>> properties) {
        if (properties.size() > 1) {
            for (GeometryProperty<?> property : properties) {
                if (property.getParent(AbstractSpaceBoundary.class) != null) {
                    return property;
                }
            }
        }

        return properties.get(0);
    }

    private class CrossLodReferenceCollector extends ObjectWalker {
        private final Map<AbstractGeometry, List<GeometryProperty<?>>> references = new IdentityHashMap<>();
        private final Map<GeometryProperty<?>, Integer> propertiesByLod = new IdentityHashMap<>();

        public Map<AbstractGeometry, List<GeometryProperty<?>>> process(AbstractFeature feature) {
            GeometryInfo geometryInfo = feature.getGeometryInfo(true);
            for (int lod : geometryInfo.getLods()) {
                geometryInfo.getGeometries(lod).forEach(property -> propertiesByLod.put(property, lod));
            }

            propertiesByLod.keySet().forEach(this::visit);
            return references;
        }

        @Override
        public void visit(GeometryProperty<?> property) {
            if (property.isSetReferencedObject() && property.getHref() != null) {
                Integer lod = getLod(property);
                Integer otherLod = getLod(property.getObject().getParent(GeometryProperty.class));
                if (lod != null && otherLod != null && !lod.equals(otherLod)) {
                    if (mode == Mode.RESOLVE || (mode == Mode.REMOVE_LOD4_REFERENCES && otherLod == 4)) {
                        references.computeIfAbsent(property.getObject(), v -> new ArrayList<>()).add(property);
                    }
                }
            }

            super.visit(property);
        }

        private Integer getLod(GeometryProperty<?> property) {
            Integer lod = null;
            if (property != null) {
                do {
                    lod = propertiesByLod.get(property);
                } while (lod == null && (property = property.getParent(GeometryProperty.class)) != null);
            }

            return lod;
        }
    }
}
