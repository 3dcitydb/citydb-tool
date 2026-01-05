/*
 * citydb-tool - Command-line tool for the 3D City Database
 * https://www.3dcitydb.org/
 *
 * Copyright 2022-2026
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

package org.citydb.model.util;

import org.citydb.model.address.Address;
import org.citydb.model.appearance.SurfaceData;
import org.citydb.model.appearance.SurfaceDataProperty;
import org.citydb.model.common.InlineOrByReferenceProperty;
import org.citydb.model.common.Referencable;
import org.citydb.model.common.Visitable;
import org.citydb.model.feature.Feature;
import org.citydb.model.geometry.ImplicitGeometry;
import org.citydb.model.property.AddressProperty;
import org.citydb.model.property.FeatureProperty;
import org.citydb.model.property.ImplicitGeometryProperty;
import org.citydb.model.walker.ModelWalker;

import java.util.*;

public class ReferenceResolver {
    private static final ReferenceResolver instance = new ReferenceResolver();

    public enum Target {
        FEATURE,
        ADDRESS,
        SURFACE_DATA,
        IMPLICIT_GEOMETRY
    }

    private ReferenceResolver() {
    }

    public static ReferenceResolver getInstance() {
        return instance;
    }

    public void resolveReferences(Feature feature, Target... targets) {
        if (feature != null) {
            resolveReferences(List.of(feature), targets);
        }
    }

    public void resolveReferences(Collection<Feature> features, Target... targets) {
        resolveReferences(features, targets != null && targets.length > 0 ?
                EnumSet.copyOf(List.of(targets)) :
                EnumSet.allOf(Target.class));
    }

    private void resolveReferences(Collection<Feature> objects, EnumSet<Target> targets) {
        if (objects == null || objects.isEmpty()) {
            return;
        }

        Map<String, Feature> features = new HashMap<>();
        Map<String, Address> addresses = new HashMap<>();
        Map<String, SurfaceData<?>> surfaceDataObjects = new HashMap<>();
        Map<String, ImplicitGeometry> implicitGeometries = new HashMap<>();

        ModelWalker collector = new ModelWalker() {
            @Override
            public void visit(Feature feature) {
                collect(feature, features, Target.FEATURE);
                super.visit(feature);
            }

            @Override
            public void visit(Address address) {
                collect(address, addresses, Target.ADDRESS);
                super.visit(address);
            }

            @Override
            public void visit(SurfaceData<?> surfaceData) {
                collect(surfaceData, surfaceDataObjects, Target.SURFACE_DATA);
                super.visit(surfaceData);
            }

            @Override
            public void visit(ImplicitGeometry implicitGeometry) {
                collect(implicitGeometry, implicitGeometries, Target.IMPLICIT_GEOMETRY);
                super.visit(implicitGeometry);
            }

            private <T extends Referencable> void collect(T object, Map<String, T> objects, Target target) {
                if (targets.contains(target)) {
                    object.getObjectId().ifPresent(objectId -> objects.put(objectId, object));
                }
            }
        };

        for (Visitable object : objects) {
            object.accept(collector);
        }

        ModelWalker resolver = new ModelWalker() {
            @Override
            public void visit(FeatureProperty property) {
                resolve(property, features);
                super.visit(property);
            }

            @Override
            public void visit(AddressProperty property) {
                resolve(property, addresses);
                super.visit(property);
            }

            @Override
            public void visit(SurfaceDataProperty property) {
                resolve(property, surfaceDataObjects);
                super.visit(property);
            }

            @Override
            public void visit(ImplicitGeometryProperty property) {
                resolve(property, implicitGeometries);
                super.visit(property);
            }

            private <R extends Referencable, T extends InlineOrByReferenceProperty<R>> void resolve(T property, Map<String, R> objects) {
                if (property.getObject().isEmpty()) {
                    property.getReference().ifPresent(reference -> {
                        R object = objects.get(reference);
                        if (object != null) {
                            property.setReference(object);
                        }
                    });
                }
            }
        };

        for (Visitable object : objects) {
            object.accept(resolver);
        }
    }
}
