/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.config.common;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import org.citydb.config.ConfigException;
import org.citydb.config.SerializableConfig;
import org.citydb.core.json.JsonMapping;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class ConfigObject<T> {
    @JsonIgnore
    private final Map<String, Object> entries = new LinkedHashMap<>();

    public static <T> ConfigObject<T> of(T config) {
        return new ConfigObject<T>().set(config);
    }

    public static <T> ConfigObject<T> of(String name, T config) {
        return new ConfigObject<T>().set(name, config);
    }

    public static <T> ConfigObject<T> of(Collection<? extends T> configs) {
        ConfigObject<T> configObject = new ConfigObject<>();
        configs.forEach(configObject::set);
        return configObject;
    }

    public static <T> ConfigObject<T> of(Map<String, ? extends T> configs) {
        ConfigObject<T> configObject = new ConfigObject<>();
        configObject.entries.putAll(configs);
        return configObject;
    }

    public static <T> ConfigObject<T> empty() {
        return new ConfigObject<>();
    }

    public <E extends T> E get(Class<E> type) throws ConfigException {
        return get(getName(type), type);
    }

    public <E extends T> E get(String name, Class<E> type) throws ConfigException {
        Object object = entries.get(name);
        if (type.isInstance(object)) {
            return type.cast(object);
        } else if (object instanceof JsonNode node) {
            try {
                E config = JsonMapping.mapper().treeToValue(node, type);
                if (config != null) {
                    entries.put(name, config);
                    return config;
                }
            } catch (JacksonException e) {
                throw new ConfigException("Failed to parse config object '" + name + "'.", e);
            }
        }

        return null;
    }

    public <E extends T> E getOrElse(Class<E> type, Supplier<E> supplier) throws ConfigException {
        return getOrElse(getName(type), type, supplier);
    }

    public <E extends T> E getOrElse(String name, Class<E> type, Supplier<E> supplier) throws ConfigException {
        return getOrCreate(name, type, supplier, false);
    }

    public <E extends T> E computeIfAbsent(Class<E> type, Supplier<E> supplier) throws ConfigException {
        return computeIfAbsent(getName(type), type, supplier);
    }

    public <E extends T> E computeIfAbsent(String name, Class<E> type, Supplier<E> supplier) throws ConfigException {
        return getOrCreate(name, type, supplier, true);
    }

    private <E extends T> E getOrCreate(String name, Class<E> type, Supplier<E> supplier, boolean putValue) throws ConfigException {
        E config = get(name, type);
        if (config == null) {
            config = supplier.get();
            if (putValue) {
                entries.put(name, config);
            }
        }

        return config;
    }

    public ConfigObject<T> set(T config) {
        return set(getName(config), config);
    }

    public ConfigObject<T> set(String name, T config) {
        entries.put(name, config);
        return this;
    }

    public <E extends T> boolean isPresent(Class<E> type) {
        return isPresent(getName(type));
    }

    public boolean isPresent(String name) {
        return entries.containsKey(name);
    }

    public <E extends T> void ifPresent(Class<E> type, Consumer<E> action) throws ConfigException {
        ifPresent(getName(type), type, action);
    }

    public <E extends T> void ifPresent(String name, Class<E> type, Consumer<E> action) throws ConfigException {
        E config = get(name, type);
        if (config != null) {
            action.accept(config);
        }
    }

    public <E extends T> boolean remove(Class<E> type) {
        return remove(getName(type));
    }

    public boolean remove(String name) {
        return entries.remove(name) != null;
    }

    public Set<String> getNames() {
        return Collections.unmodifiableSet(entries.keySet());
    }

    @JsonAnySetter
    private void setEntry(String name, JsonNode node) {
        entries.put(name, node);
    }

    @JsonAnyGetter
    private Map<String, Object> getEntries() {
        return entries;
    }

    private String getName(Object config) {
        return getName(config.getClass());
    }

    private String getName(Class<?> type) {
        return type.isAnnotationPresent(SerializableConfig.class)
                ? type.getAnnotation(SerializableConfig.class).name()
                : type.getName();
    }
}
