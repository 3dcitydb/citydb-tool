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

package org.citydb.operation.util;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;
import org.citydb.database.adapter.DatabaseAdapter;
import org.citydb.database.schema.SchemaMapping;
import org.citydb.logging.LoggerManager;
import org.citydb.model.appearance.Appearance;
import org.citydb.model.common.Name;
import org.citydb.model.common.Namespaces;
import org.citydb.model.feature.Feature;
import org.citydb.model.walker.ModelWalker;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class FeatureStatistics {
    private final Logger logger = LoggerManager.getInstance().getLogger(FeatureStatistics.class);
    private final SchemaMapping schemaMapping;
    private final Counter counter = new Counter();
    private final Map<Integer, Long> features = new ConcurrentHashMap<>();

    public FeatureStatistics(SchemaMapping schemaMapping) {
        this.schemaMapping = Objects.requireNonNull(schemaMapping, "The schema mapping must not be null.");
    }

    public FeatureStatistics(DatabaseAdapter adapter) {
        this(adapter.getSchemaAdapter().getSchemaMapping());
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

    public void logFeatureSummary(Level level) {
        printFeatureSummary(s -> logger.log(level, s));
    }

    public void logFeatureSummary(Level level, Runnable whenEmptyAction) {
        printFeatureSummary(s -> logger.log(level, s), whenEmptyAction);
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
        Name featureType = schemaMapping.getFeatureType(entry.getKey()).getName();
        String alias = schemaMapping.getNamespaceByURI(featureType.getNamespace()).getAlias().orElse(null);
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
            add(schemaMapping.getFeatureType(featureType).getId());
        }

        @Override
        public void visit(Feature feature) {
            add(schemaMapping.getFeatureType(feature.getFeatureType()).getId());
            super.visit(feature);
        }
    }
}
