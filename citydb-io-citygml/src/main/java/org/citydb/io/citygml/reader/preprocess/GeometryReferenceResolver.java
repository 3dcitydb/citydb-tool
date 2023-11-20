/*
 * citydb-tool - Command-line tool for the 3D City Database
 * https://www.3dcitydb.org/
 *
 * Copyright 2022-2023
 * Virtual City Systems, Germany
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

import org.citydb.io.citygml.reader.util.FeatureHelper;
import org.citygml4j.core.model.core.*;
import org.citygml4j.core.util.reference.DefaultReferenceResolver;
import org.citygml4j.core.util.reference.ResolveMode;
import org.citygml4j.core.visitor.ObjectWalker;
import org.xmlobjects.gml.model.basictypes.Code;
import org.xmlobjects.gml.model.geometry.AbstractGeometry;
import org.xmlobjects.gml.model.geometry.GeometryProperty;
import org.xmlobjects.gml.util.id.DefaultIdCreator;
import org.xmlobjects.util.copy.CopyBuilder;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public class GeometryReferenceResolver {
    private final Supplier<CopyBuilder> copyBuilderSupplier;
    private final Map<String, GeometryReference> references = new ConcurrentHashMap<>();
    private final GeometryProcessor geometryProcessor = new GeometryProcessor();

    private boolean createCityObjectRelations;

    GeometryReferenceResolver(Supplier<CopyBuilder> copyBuilderSupplier) {
        this.copyBuilderSupplier = copyBuilderSupplier;
    }

    GeometryReferenceResolver createCityObjectRelations(boolean createCityObjectRelations) {
        this.createCityObjectRelations = createCityObjectRelations;
        return this;
    }

    void processGeometryReferences(AbstractFeature feature, int featureId) {
        DefaultReferenceResolver.newInstance()
                .withResolveMode(ResolveMode.GEOMETRIES_ONLY)
                .resolveReferences(feature);

        GeometryPropertyProcessor processor = new GeometryPropertyProcessor();
        processor.process(feature, featureId);
    }

    void processReferencedGeometries(AbstractFeature feature) {
        feature.accept(geometryProcessor);
    }

    boolean hasReferences() {
        return !references.isEmpty();
    }

    void resolveGeometryReferences(AbstractFeature feature, int featureId) {
        if (!references.isEmpty()) {
            ResolverProcessor processor = new ResolverProcessor();
            processor.resolve(feature, featureId);
        }
    }

    private class ResolverProcessor extends ObjectWalker {
        private final Map<AbstractCityObject, Integer> childIds = new IdentityHashMap<>();
        private final Map<AbstractCityObject, Set<String>> relatedTos = new IdentityHashMap<>();
        private int featureId;

        void resolve(AbstractFeature feature, int featureId) {
            this.featureId = featureId;
            feature.accept(this);
            childIds.clear();
            relatedTos.clear();
        }

        @Override
        public void visit(AbstractCityObject cityObject) {
            childIds.put(cityObject, childIds.size());
            super.visit(cityObject);
        }

        @Override
        public void visit(AbstractGeometry geometry) {
            if (createCityObjectRelations && geometry.getId() != null) {
                GeometryReference reference = references.get(geometry.getId());
                if (reference != null) {
                    AbstractCityObject cityObject = geometry.getParent(AbstractCityObject.class);
                    if (cityObject != null) {
                        cityObject.setId(reference.getOwner());
                        for (String relatedTo : reference.getRelatedTos()) {
                            if (relatedTos.computeIfAbsent(cityObject, v -> new HashSet<>()).add(relatedTo)) {
                                CityObjectRelation relation = new CityObjectRelation("#" + relatedTo);
                                relation.setRelationType(new Code("shared"));
                                cityObject.getRelatedTo().add(new CityObjectRelationProperty(relation));
                            }
                        }
                    }
                }
            }
        }

        @Override
        public void visit(GeometryProperty<?> property) {
            if (property.getObject() == null
                    && property.getHref() != null
                    && property.getParent(ImplicitGeometry.class) == null) {
                GeometryReference reference = references.get(FeatureHelper.getIdFromReference(property.getHref()));
                if (reference != null && reference.geometry != null) {
                    AbstractCityObject cityObject = property.getParent(AbstractCityObject.class);
                    if (cityObject != null) {
                        String target = reference.getTarget(featureId, childIds.get(cityObject));
                        if (target != null) {
                            AbstractGeometry geometry = reference.createGeometryFor(featureId, copyBuilderSupplier);
                            property.setInlineObjectIfValid(geometry);
                            property.setHref(null);

                            if (createCityObjectRelations
                                    && relatedTos.computeIfAbsent(cityObject, v -> new HashSet<>())
                                    .add(reference.getOwner())) {
                                cityObject.setId(target);
                                CityObjectRelation relation = new CityObjectRelation("#" + reference.getOwner());
                                relation.setRelationType(new Code("shared"));
                                cityObject.getRelatedTo().add(new CityObjectRelationProperty(relation));
                            }
                        } else {
                            property.setHref("#" + reference.getOrCreateGeometryId(featureId));
                        }
                    }
                }
            } else if (!property.isSetReferencedObject()) {
                super.visit(property);
            }
        }
    }

    private class GeometryPropertyProcessor extends ObjectWalker {
        private final Map<String, Deque<AbstractCityObject>> referees = new HashMap<>();
        private final Map<AbstractCityObject, Integer> childIds = new IdentityHashMap<>();

        void process(AbstractFeature feature, int featureId) {
            feature.accept(this);

            for (Map.Entry<String, Deque<AbstractCityObject>> entry : referees.entrySet()) {
                Deque<AbstractCityObject> candidates = entry.getValue();
                candidates.stream().filter(AbstractSpaceBoundary.class::isInstance)
                        .map(AbstractSpaceBoundary.class::cast)
                        .map(boundary -> boundary.getParent(AbstractSpace.class))
                        .forEach(space -> candidates.removeIf(space::equals));

                GeometryReference reference = references.computeIfAbsent(entry.getKey(), v -> new GeometryReference());
                reference.addTarget(candidates.getFirst(), featureId, childIds.get(candidates.getFirst()));
            }

            referees.clear();
            childIds.clear();
        }

        @Override
        public void visit(AbstractCityObject cityObject) {
            childIds.put(cityObject, childIds.size());
            super.visit(cityObject);
        }

        @Override
        public void visit(GeometryProperty<?> property) {
            if (property.getObject() == null
                    && property.getHref() != null
                    && property.getParent(ImplicitGeometry.class) == null) {
                AbstractCityObject cityObject = property.getParent(AbstractCityObject.class);
                if (cityObject != null) {
                    String reference = FeatureHelper.getIdFromReference(property.getHref());
                    referees.computeIfAbsent(reference, v -> new ArrayDeque<>()).add(cityObject);
                }
            } else {
                super.visit(property);
            }
        }
    }

    private class GeometryProcessor extends ObjectWalker {

        @Override
        public void visit(AbstractGeometry geometry) {
            if (geometry.getId() != null) {
                GeometryReference reference = references.get(geometry.getId());
                if (reference != null) {
                    reference.setGeometry(geometry);
                    reference.setOwner(geometry.getParent(AbstractCityObject.class));
                }
            }
        }
    }

    private static class GeometryReference {
        private AbstractGeometry geometry;
        private String owner;
        private final Map<Integer, String> geometryIds = new HashMap<>();
        private final Map<String, String> targets = new HashMap<>();

        AbstractGeometry createGeometryFor(int featureId, Supplier<CopyBuilder> copyBuilderSupplier) {
            GeometryCopyBuilder copyBuilder = new GeometryCopyBuilder(copyBuilderSupplier);
            AbstractGeometry copy = copyBuilder.copy(geometry);
            copy.setId(getOrCreateGeometryId(featureId));
            return copy;
        }

        void setGeometry(AbstractGeometry geometry) {
            this.geometry = geometry;
        }

        String getOrCreateGeometryId(int featureId) {
            return geometryIds.computeIfAbsent(featureId, v -> DefaultIdCreator.getInstance().createId());
        }

        String getOwner() {
            return owner;
        }

        void setOwner(AbstractCityObject owner) {
            this.owner = FeatureHelper.getOrCreateId(owner);
        }

        Collection<String> getRelatedTos() {
            return targets.values();
        }

        String getTarget(int featureId, int childId) {
            return targets.get(featureId + "_" + childId);
        }

        void addTarget(AbstractCityObject cityObject, int featureId, int childId) {
            targets.put(featureId + "_" + childId, FeatureHelper.getOrCreateId(cityObject));
        }
    }
}
