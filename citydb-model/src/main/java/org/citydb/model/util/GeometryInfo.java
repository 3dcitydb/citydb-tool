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

package org.citydb.model.util;

import org.citydb.model.property.GeometryProperty;
import org.citydb.model.property.ImplicitGeometryProperty;

import java.util.*;
import java.util.stream.Collectors;

public class GeometryInfo {
    private final Map<String, List<GeometryProperty>> geometries = new HashMap<>();
    private final Map<String, List<ImplicitGeometryProperty>> implicitGeometries = new HashMap<>();

    public enum Mode {
        SKIP_NESTED_FEATURES,
        INCLUDE_CONTAINED_FEATURES,
        INCLUDE_ALL_NESTED_FEATURES
    }

    public List<GeometryProperty> getGeometries() {
        return geometries.values().stream()
                .flatMap(Collection::stream)
                .collect(Collectors.toList());
    }

    public List<GeometryProperty> getGeometries(String lod) {
        return geometries.getOrDefault(lod, Collections.emptyList());
    }

    public List<GeometryProperty> getNonLodGeometries() {
        return geometries.getOrDefault(null, Collections.emptyList());
    }

    public void add(GeometryProperty property) {
        if (property != null) {
            geometries.computeIfAbsent(property.getLod().orElse(null), v -> new ArrayList<>())
                    .add(property);
        }
    }

    public List<ImplicitGeometryProperty> getImplicitGeometries() {
        return implicitGeometries.values().stream()
                .flatMap(Collection::stream)
                .collect(Collectors.toList());
    }

    public List<ImplicitGeometryProperty> getImplicitGeometries(String lod) {
        return implicitGeometries.getOrDefault(lod, Collections.emptyList());
    }

    public List<ImplicitGeometryProperty> getNonLodImplicitGeometries() {
        return implicitGeometries.getOrDefault(null, Collections.emptyList());
    }

    public void add(ImplicitGeometryProperty property) {
        if (property != null) {
            implicitGeometries.computeIfAbsent(property.getLod().orElse(null), v -> new ArrayList<>())
                    .add(property);
        }
    }

    public Set<String> getLods() {
        Set<String> lods = getLods(geometries);
        lods.addAll(getLods(implicitGeometries));
        return lods;
    }

    private Set<String> getLods(Map<String, ?> geometries) {
        return geometries.keySet().stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }
}
