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

package org.citydb.model.feature;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class FeatureCollection implements Serializable {
    private final List<Feature> features;

    private FeatureCollection(List<Feature> features) {
        this.features = Objects.requireNonNull(features, "The feature collection must not be null.");
    }

    private FeatureCollection(Feature[] features) {
        Objects.requireNonNull(features, "The feature collection must not be null.");
        this.features = Arrays.asList(features);
    }

    public static FeatureCollection of(List<Feature> features) {
        return new FeatureCollection(features);
    }

    public static FeatureCollection of(Feature... features) {
        return new FeatureCollection(features);
    }

    public static FeatureCollection empty() {
        return new FeatureCollection(new ArrayList<>());
    }

    public List<Feature> getFeatures() {
        return features;
    }

    public FeatureCollection addFeature(Feature feature) {
        if (feature != null) {
            features.add(feature);
        }

        return this;
    }

    public int size() {
        return features.size();
    }

    public boolean isEmpty() {
        return features.isEmpty();
    }

    public void clear() {
        features.clear();
    }
}
