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

package org.citydb.config.common;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.citydb.config.ConfigException;
import org.citydb.config.SerializableConfig;

import java.util.LinkedHashMap;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class ConfigObject<T> extends LinkedHashMap<String, T> {

    public <E extends T> E get(Class<E> type) throws ConfigException {
        return get(getName(type), type);
    }

    public <E extends T> E get(String name, Class<E> type) throws ConfigException {
        Object object = get(name);
        if (type.isInstance(object)) {
            return type.cast(object);
        } else if (object instanceof JSONObject) {
            try {
                E config = JSON.parseObject(object.toString(), type);
                if (config != null) {
                    set(name, config);
                    return config;
                }
            } catch (Exception e) {
                throw new ConfigException("Failed to parse JSON config object.", e);
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
                put(name, config);
            }
        }

        return config;
    }

    public ConfigObject<T> set(T config) {
        return set(getName(config), config);
    }

    public ConfigObject<T> set(String name, T config) {
        put(name, config);
        return this;
    }

    public <E extends T> boolean isPresent(Class<E> type) {
        return isPresent(getName(type));
    }

    public boolean isPresent(String name) {
        return containsKey(name);
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

    private String getName(Object config) {
        return getName(config.getClass());
    }

    private String getName(Class<?> type) {
        return type.isAnnotationPresent(SerializableConfig.class) ?
                type.getAnnotation(SerializableConfig.class).name() :
                type.getName();
    }
}
