/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.model.common;

import org.citydb.core.function.CheckedConsumer;
import org.citydb.model.property.DataTypeProvider;
import org.citydb.model.property.Property;

import java.io.Serializable;
import java.util.*;
import java.util.function.Predicate;

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

    public PropertyMap(Child parent, Collection<T> c) {
        this(parent);
        addAll(c);
    }

    public Child getParent() {
        return parent;
    }

    void setParent(Child parent) {
        this.parent = parent;
        applyParent(this);
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
        List<T> result = new ArrayList<>();
        for (T property : get(name)) {
            if (property.hasDataType(dataType)) {
                result.add(property);
            }
        }

        return result;
    }

    public List<T> get(Name name, DataTypeProvider provider) {
        return get(name, provider.getName());
    }

    public <R extends Property<?>> List<R> get(Name name, Class<R> type) {
        return get(get(name), type);
    }

    public <R extends Property<?>> List<R> get(Name name, Name dataType, Class<R> type) {
        return get(get(name, dataType), type);
    }

    public <R extends Property<?>> List<R> get(Name name, DataTypeProvider provider, Class<R> type) {
        return get(get(name, provider.getName()), type);
    }

    private <R extends Property<?>> List<R> get(List<T> properties, Class<R> type) {
        List<R> result = new ArrayList<>();
        for (T property : properties) {
            if (type.isInstance(property)) {
                result.add(type.cast(property));
            }
        }

        return result;
    }

    public Optional<T> getFirst(Name name) {
        List<T> properties = get(name);
        return !properties.isEmpty()
                ? Optional.of(properties.get(0))
                : Optional.empty();
    }

    public Optional<T> getFirst(Name name, Name dataType) {
        for (T property : get(name)) {
            if (property.hasDataType(dataType)) {
                return Optional.of(property);
            }
        }

        return Optional.empty();
    }

    public Optional<T> getFirst(Name name, DataTypeProvider provider) {
        return getFirst(name, provider.getName());
    }

    public <R extends Property<?>> Optional<R> getFirst(Name name, Class<R> type) {
        return getFirst(getFirst(name).orElse(null), type);
    }

    public <R extends Property<?>> Optional<R> getFirst(Name name, Name dataType, Class<R> type) {
        return getFirst(getFirst(name, dataType).orElse(null), type);
    }

    public <R extends Property<?>> Optional<R> getFirst(Name name, DataTypeProvider provider, Class<R> type) {
        return getFirst(getFirst(name, provider.getName()).orElse(null), type);
    }

    private <R extends Property<?>> Optional<R> getFirst(T property, Class<R> type) {
        return type.isInstance(property)
                ? Optional.of(type.cast(property))
                : Optional.empty();
    }

    public boolean containsNamespace(String namespace) {
        return elements.containsKey(Namespaces.ensureNonNull(namespace));
    }

    public List<T> getByNamespace(String namespace) {
        List<T> result = new ArrayList<>();
        Collection<List<T>> elements = this.elements
                .getOrDefault(Namespaces.ensureNonNull(namespace), Collections.emptyMap())
                .values();

        for (List<T> properties : elements) {
            result.addAll(properties);
        }

        return result;
    }

    public <E extends Exception> void forEachByNamespace(String namespace, CheckedConsumer<T, E> action) throws E {
        Collection<List<T>> values = elements
                .getOrDefault(Namespaces.ensureNonNull(namespace), Collections.emptyMap())
                .values();

        for (List<T> properties : values) {
            for (T property : properties) {
                action.accept(property);
            }
        }
    }

    public <R extends Property<?>> List<R> getByNamespace(String namespace, Class<R> type) {
        List<R> result = new ArrayList<>();
        Collection<List<T>> elements = this.elements
                .getOrDefault(Namespaces.ensureNonNull(namespace), Collections.emptyMap())
                .values();

        for (List<T> properties : elements) {
            for (T property : properties) {
                if (type.isInstance(property)) {
                    result.add(type.cast(property));
                }
            }
        }

        return result;
    }

    public <R extends Property<?>, E extends Exception> void forEachByNamespace(String namespace, Class<R> type, CheckedConsumer<R, E> action) throws E {
        Collection<List<T>> values = elements
                .getOrDefault(Namespaces.ensureNonNull(namespace), Collections.emptyMap())
                .values();

        for (List<T> properties : values) {
            for (T property : properties) {
                if (type.isInstance(property)) {
                    action.accept(type.cast(property));
                }
            }
        }
    }

    public List<T> getAll() {
        List<T> result = new ArrayList<>();
        for (Map<String, List<T>> values : elements.values()) {
            for (List<T> properties : values.values()) {
                result.addAll(properties);
            }
        }

        return result;
    }

    public <E extends Exception> void forEach(CheckedConsumer<T, E> action) throws E {
        for (Map<String, List<T>> values : elements.values()) {
            for (List<T> properties : values.values()) {
                for (T property : properties) {
                    action.accept(property);
                }
            }
        }
    }

    public List<T> getIf(Predicate<T> predicate) {
        List<T> result = new ArrayList<>();
        for (Map<String, List<T>> values : elements.values()) {
            for (List<T> properties : values.values()) {
                for (T property : properties) {
                    if (predicate.test(property)) {
                        result.add(property);
                    }
                }
            }
        }

        return result;
    }

    public <E extends Exception> void forEachIf(Predicate<T> predicate, CheckedConsumer<T, E> action) throws E {
        for (Map<String, List<T>> values : elements.values()) {
            for (List<T> properties : values.values()) {
                for (T property : properties) {
                    if (predicate.test(property)) {
                        action.accept(property);
                    }
                }
            }
        }
    }

    public List<T> getIfNamespace(Predicate<String> predicate) {
        List<T> result = new ArrayList<>();
        for (Map.Entry<String, Map<String, List<T>>> entry : elements.entrySet()) {
            if (predicate.test(entry.getKey())) {
                for (List<T> properties : entry.getValue().values()) {
                    result.addAll(properties);
                }
            }
        }

        return result;
    }

    public <E extends Exception> void forEachIfNamespace(Predicate<String> predicate, CheckedConsumer<T, E> action) throws E {
        for (Map.Entry<String, Map<String, List<T>>> entry : elements.entrySet()) {
            if (predicate.test(entry.getKey())) {
                for (List<T> properties : entry.getValue().values()) {
                    for (T property : properties) {
                        action.accept(property);
                    }
                }
            }
        }
    }

    /**
     * @deprecated Use {@link #add(T)} instead.
     */
    @Deprecated(since = "1.4", forRemoval = true)
    public void put(T element) {
        add(element);
    }

    /**
     * @deprecated Use {@link #addAll(Collection)} instead.
     */
    @Deprecated(since = "1.4", forRemoval = true)
    public void putAll(Collection<T> elements) {
        addAll(elements);
    }

    public void add(T element) {
        if (element != null) {
            elements.computeIfAbsent(element.getName().getNamespace(), v -> new LinkedHashMap<>())
                    .computeIfAbsent(element.getName().getLocalName(), v -> new ChildList<>(parent))
                    .add(element);
        }
    }

    public void addAll(Collection<T> elements) {
        if (elements != null) {
            for (T element : elements) {
                if (element != null) {
                    add(element);
                }
            }
        }
    }

    public void set(T element) {
        if (element != null) {
            elements.computeIfAbsent(element.getName().getNamespace(), v -> new LinkedHashMap<>())
                    .put(element.getName().getLocalName(), new ChildList<>(List.of(element), parent));
        }
    }

    public void setAll(Collection<T> elements) {
        if (elements != null) {
            Set<Name> replaced = new HashSet<>();
            for (T element : elements) {
                if (element != null) {
                    Map<String, List<T>> values = this.elements.computeIfAbsent(element.getName().getNamespace(),
                            v -> new LinkedHashMap<>());
                    List<T> properties;
                    if (replaced.add(element.getName())) {
                        properties = new ChildList<>(parent);
                        values.put(element.getName().getLocalName(), properties);
                    } else {
                        properties = values.get(element.getName().getLocalName());
                    }

                    properties.add(element);
                }
            }
        }
    }

    public boolean remove(T element) {
        if (element == null) {
            return false;
        }

        Map<String, List<T>> values = elements.get(element.getName().getNamespace());
        if (values == null) {
            return false;
        }

        List<T> properties = values.get(element.getName().getLocalName());
        if (properties == null || !properties.remove(element)) {
            return false;
        }

        if (properties.isEmpty()) {
            values.remove(element.getName().getLocalName());
            if (values.isEmpty()) {
                elements.remove(element.getName().getNamespace());
            }
        }

        return true;
    }

    public List<T> remove(Name name) {
        if (name == null) {
            return new ArrayList<>();
        }

        Map<String, List<T>> values = elements.get(name.getNamespace());
        if (values == null) {
            return new ArrayList<>();
        }

        List<T> properties = values.remove(name.getLocalName());
        if (values.isEmpty()) {
            elements.remove(name.getNamespace());
        }

        return properties != null
                ? properties :
                new ArrayList<>();
    }

    public List<T> removeByNamespace(String namespace) {
        List<T> result = new ArrayList<>();
        Map<String, List<T>> values = elements.remove(Namespaces.ensureNonNull(namespace));
        if (values != null) {
            for (List<T> properties : values.values()) {
                result.addAll(properties);
            }
        }

        return result;
    }

    public Set<String> getNamespaces() {
        return Collections.unmodifiableSet(elements.keySet());
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

    private void applyParent(PropertyMap<T> map) {
        for (Map<String, List<T>> values : map.elements.values()) {
            for (List<T> properties : values.values()) {
                ((ChildList<?>) properties).setParent(parent);
            }
        }
    }
}
