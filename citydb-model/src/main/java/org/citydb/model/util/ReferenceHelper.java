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
import org.citydb.model.common.Referencable;
import org.citydb.model.feature.Feature;
import org.citydb.model.geometry.ImplicitGeometry;
import org.citydb.model.walker.ModelWalker;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class ReferenceHelper {
    private final Map<String, Feature> features = new HashMap<>();
    private final Map<String, Address> addresses = new HashMap<>();
    private final Map<String, SurfaceData<?>> surfaceDataObjects = new HashMap<>();
    private final Map<String, ImplicitGeometry> implicitGeometries = new HashMap<>();

    private ReferenceHelper() {
    }

    public static ReferenceHelper of(Feature... features) {
        return new ReferenceHelper().initialize(Arrays.asList(features));
    }

    public static ReferenceHelper of(Collection<Feature> features) {
        return new ReferenceHelper().initialize(features);
    }

    public Feature lookupFeature(String objectId) {
        return objectId != null ? features.get(objectId) : null;
    }

    public Address lookupAddress(String objectId) {
        return objectId != null ? addresses.get(objectId) : null;
    }

    public SurfaceData<?> lookupSurfaceData(String objectId) {
        return objectId != null ? surfaceDataObjects.get(objectId) : null;
    }

    public ImplicitGeometry lookupImplicitGeometry(String objectId) {
        return objectId != null ? implicitGeometries.get(objectId) : null;
    }

    private ReferenceHelper initialize(Collection<Feature> objects) {
        if (objects == null || objects.isEmpty()) {
            return this;
        }

        ModelWalker walker = new ModelWalker() {
            @Override
            public void visit(Feature feature) {
                collect(feature, features);
                super.visit(feature);
            }

            @Override
            public void visit(Address address) {
                collect(address, addresses);
                super.visit(address);
            }

            @Override
            public void visit(SurfaceData<?> surfaceData) {
                collect(surfaceData, surfaceDataObjects);
                super.visit(surfaceData);
            }

            @Override
            public void visit(ImplicitGeometry implicitGeometry) {
                collect(implicitGeometry, implicitGeometries);
                super.visit(implicitGeometry);
            }

            private <T extends Referencable> void collect(T object, Map<String, T> objects) {
                object.getObjectId().ifPresent(objectId -> objects.put(objectId, object));
            }
        };

        objects.forEach(walker::visit);
        return this;
    }
}
