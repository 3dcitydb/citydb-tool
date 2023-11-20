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

package org.citydb.operation.util;

import org.citydb.database.schema.NamespaceHelper;
import org.citydb.database.schema.ObjectClassHelper;
import org.citydb.model.appearance.Appearance;
import org.citydb.model.common.Name;
import org.citydb.model.common.Namespaces;
import org.citydb.model.feature.Feature;
import org.citydb.model.walker.ModelWalker;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class FeatureStatistics {
    private final ObjectClassHelper objectClassHelper;
    private final NamespaceHelper namespaceHelper;
    private final Counter counter = new Counter();
    private final Map<Integer, Long> features = new ConcurrentHashMap<>();

    public FeatureStatistics(ObjectClassHelper objectClassHelper, NamespaceHelper namespaceHelper) {
        this.objectClassHelper = objectClassHelper;
        this.namespaceHelper = namespaceHelper;
    }

    public void add(Feature feature) {
        feature.accept(counter);
    }

    public void add(int objectClassId) {
        features.merge(objectClassId, 1L, Long::sum);
    }

    public void merge(FeatureStatistics other) {
        if (other != null) {
            other.features.forEach((key, value) -> features.merge(key, value, Long::sum));
        }
    }

    public Map<Integer, Long> getFeatureCount() {
        return features;
    }

    public boolean isEmpty() {
        return features.isEmpty();
    }

    public void printFeatureSummary(Consumer<String> consumer) {
        features.entrySet().stream()
                .map(this::mapToString)
                .sorted()
                .forEach(consumer);
    }

    public void printFeatureSummary(Consumer<String> consumer, Runnable whenEmptyAction) {
        if (!features.isEmpty()) {
            printFeatureSummary(consumer);
        } else {
            whenEmptyAction.run();
        }
    }

    private String mapToString(Map.Entry<Integer, Long> entry) {
        Name featureType = objectClassHelper.getObjectClass(entry.getKey()).getName();
        String alias = namespaceHelper != null ? namespaceHelper.getAlias(featureType.getNamespace()) : null;
        String qName = alias != null ? alias + ":" + featureType.getLocalName() : featureType.getLocalName();
        return qName + ": " + entry.getValue();
    }

    public void clear() {
        features.clear();
    }

    private class Counter extends ModelWalker {
        @Override
        public void visit(Appearance appearance) {
            Name featureType = Name.of(appearance.getClass().getSimpleName(), Namespaces.APPEARANCE);
            add(objectClassHelper.getObjectClass(featureType).getId());
        }

        @Override
        public void visit(Feature feature) {
            add(objectClassHelper.getObjectClass(feature.getFeatureType()).getId());
            super.visit(feature);
        }
    }
}
