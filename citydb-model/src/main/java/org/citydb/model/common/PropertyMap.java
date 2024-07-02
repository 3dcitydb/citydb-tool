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

package org.citydb.model.common;

import org.citydb.model.property.DataTypeProvider;
import org.citydb.model.property.Property;

import java.io.Serializable;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class PropertyMap<T extends Property<?>> implements Serializable {
    private final Map<String, Map<String, List<T>>> elements;
    private Child parent;

    public PropertyMap(Child parent) {
        this.parent = Objects.requireNonNull(parent, "The parent must not be null.");
        elements = new LinkedHashMap<>();
    }

    public PropertyMap(Child parent, int initialCapacity) {
        this.parent = Objects.requireNonNull(parent, "The parent must not be null.");
        elements = new LinkedHashMap<>(initialCapacity);
    }

    public PropertyMap(Child parent, int initialCapacity, float loadFactor) {
        this.parent = Objects.requireNonNull(parent, "The parent must not be null.");
        elements = new LinkedHashMap<>(initialCapacity, loadFactor);
    }

    public PropertyMap(Child parent, PropertyMap<T> m) {
        this.parent = Objects.requireNonNull(parent, "The parent must not be null.");
        applyParent(m);
        elements = new LinkedHashMap<>(m.elements);
    }

    public PropertyMap(Child parent, Collection<T> c) {
        this(parent);
        putAll(c);
    }

    public Child getParent() {
        return parent;
    }

    void setParent(Child parent) {
        this.parent = parent;
        applyParent(this);
    }

    public int size() {
        return elements.size();
    }

    public boolean isEmpty() {
        return elements.isEmpty();
    }

    public void clear() {
        elements.clear();
    }

    public List<T> get(Name name) {
        return elements.getOrDefault(name.getNamespace(), Collections.emptyMap())
                .getOrDefault(name.getLocalName(), Collections.emptyList());
    }

    public List<T> get(Name name, Name dataType) {
        return get(name).stream()
                .filter(element -> element.hasDataType(dataType))
                .collect(Collectors.toList());
    }

    public List<T> get(Name name, DataTypeProvider provider) {
        return get(name, provider.getName());
    }

    public <R extends Property<?>> List<R> get(Name name, Class<R> type) {
        return get(name).stream()
                .filter(type::isInstance)
                .map(type::cast)
                .collect(Collectors.toList());
    }

    public Optional<T> getFirst(Name name) {
        return get(name).stream().findFirst();
    }

    public Optional<T> getFirst(Name name, Name dataType) {
        return get(name).stream()
                .filter(element -> element.hasDataType(dataType))
                .findFirst();
    }

    public Optional<T> getFirst(Name name, DataTypeProvider provider) {
        return getFirst(name, provider.getName());
    }

    @SuppressWarnings("unchecked")
    public <R extends Property<?>> Optional<R> getFirst(Name name, Class<R> type) {
        Optional<T> result = getFirst(name);
        return result.isPresent() && type.isInstance(result.get()) ?
                (Optional<R>) result :
                Optional.empty();
    }

    public boolean containsNamespace(String namespace) {
        return elements.containsKey(Namespaces.ensureNonNull(namespace));
    }

    public List<T> getByNamespace(String namespace) {
        return elements.getOrDefault(Namespaces.ensureNonNull(namespace), Collections.emptyMap())
                .values().stream()
                .flatMap(List::stream)
                .collect(Collectors.toList());
    }

    public <R extends Property<?>> List<R> getByNamespace(String namespace, Class<R> type) {
        return elements.getOrDefault(Namespaces.ensureNonNull(namespace), Collections.emptyMap())
                .values().stream()
                .flatMap(List::stream)
                .filter(type::isInstance)
                .map(type::cast)
                .collect(Collectors.toList());
    }

    public List<T> getAll() {
        return elements.values().stream()
                .map(Map::values)
                .flatMap(Collection::stream)
                .flatMap(List::stream)
                .collect(Collectors.toList());
    }

    public List<T> getIf(Predicate<T> predicate) {
        return getAll().stream()
                .filter(predicate)
                .collect(Collectors.toList());
    }

    public List<T> getIfNamespace(Predicate<String> predicate) {
        return elements.entrySet().stream()
                .filter(e -> predicate.test(e.getKey()))
                .map(Map.Entry::getValue)
                .map(Map::values)
                .flatMap(Collection::stream)
                .flatMap(List::stream)
                .collect(Collectors.toList());
    }

    public void put(T element) {
        if (element != null) {
            elements.computeIfAbsent(element.getName().getNamespace(), v -> new LinkedHashMap<>())
                    .computeIfAbsent(element.getName().getLocalName(), v -> new ChildList<>(parent))
                    .add(element);
        }
    }

    public void putAll(Collection<T> elements) {
        if (elements != null) {
            elements.stream().filter(Objects::nonNull).forEach(this::put);
        }
    }

    public void putAll(PropertyMap<T> elements) {
        if (elements != null) {
            putAll(elements.getAll());
        }
    }

    public boolean remove(T element) {
        return elements.getOrDefault(element.getName().getNamespace(), Collections.emptyMap())
                .getOrDefault(element.getName().getLocalName(), Collections.emptyList())
                .remove(element);
    }

    public List<T> remove(Name name) {
        return elements.getOrDefault(name.getNamespace(), Collections.emptyMap())
                .remove(name.getLocalName());
    }

    public Optional<T> removeAndGetFirst(Name name) {
        List<T> elements = remove(name);
        return !elements.isEmpty() ?
                Optional.ofNullable(elements.get(0)) :
                Optional.empty();
    }

    public Optional<T> removeAndGetFirst(Name name, Name dataType) {
        return remove(name).stream()
                .filter(element -> element.hasDataType(dataType))
                .findFirst();
    }

    public Optional<T> removeAndGetFirst(Name name, DataTypeProvider provider) {
        return removeAndGetFirst(name, provider.getName());
    }

    public List<T> removeByNamespace(String namespace) {
        return elements.remove(Namespaces.ensureNonNull(namespace))
                .values().stream()
                .flatMap(List::stream)
                .collect(Collectors.toList());
    }

    public Set<String> getNamespaces() {
        return elements.keySet();
    }

    public void sortPropertiesWithIdenticalNames(Comparator<Property<?>> comparator) {
        for (Map<String, List<T>> values : elements.values()) {
            for (List<T> properties : values.values()) {
                if (properties.size() > 1) {
                    properties.sort(comparator);
                }
            }
        }
    }

    private void applyParent(PropertyMap<T> m) {
        m.elements.values().stream()
                .map(Map::values)
                .flatMap(Collection::stream)
                .map(ChildList.class::cast)
                .forEach(childList -> childList.setParent(parent));
    }
}
