/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
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

    public boolean hasGeometries() {
        return !geometries.isEmpty();
    }

    public List<GeometryProperty> getGeometries() {
        return geometries.values().stream()
                .flatMap(Collection::stream)
                .toList();
    }

    public List<GeometryProperty> getGeometries(String lod) {
        return geometries.getOrDefault(lod, Collections.emptyList());
    }

    public void add(GeometryProperty property) {
        if (property != null) {
            geometries.computeIfAbsent(property.getLod().orElse(null), v -> new ArrayList<>())
                    .add(property);
        }
    }

    public boolean hasImplicitGeometries() {
        return !implicitGeometries.isEmpty();
    }

    public List<ImplicitGeometryProperty> getImplicitGeometries() {
        return implicitGeometries.values().stream()
                .flatMap(Collection::stream)
                .toList();
    }

    public List<ImplicitGeometryProperty> getImplicitGeometries(String lod) {
        return implicitGeometries.getOrDefault(lod, Collections.emptyList());
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
