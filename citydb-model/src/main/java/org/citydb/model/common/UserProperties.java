/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.model.common;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

public class UserProperties implements Serializable {
    private Map<String, Object> properties;

    public Object get(String name) {
        return properties != null ? properties.get(name) : null;
    }

    public <T> T get(String name, Class<T> type) {
        if (properties != null) {
            Object value = properties.get(name);
            return value != null && type.isAssignableFrom(value.getClass()) ? type.cast(value) : null;
        } else {
            return null;
        }
    }

    public boolean getAndCompare(String name, Object expectedValue) {
        return Objects.equals(get(name), expectedValue);
    }

    public <T> T getOrDefault(String name, Class<T> type, Supplier<T> supplier) {
        T value = get(name, type);
        return value != null ? value : supplier.get();
    }

    public <T> T getOrSet(String name, Class<T> type, Supplier<T> supplier) {
        T value = get(name, type);
        if (value == null) {
            value = supplier.get();
            set(name, value);
        }

        return value;
    }

    public boolean contains(String name) {
        return properties != null && properties.containsKey(name);
    }

    public UserProperties set(String name, Object value) {
        if (properties == null) {
            properties = new HashMap<>();
        }

        properties.put(name, value);
        return this;
    }

    public boolean isEmpty() {
        return properties == null || properties.isEmpty();
    }

    public void remove(String name) {
        if (properties != null) {
            properties.remove(name);
        }
    }

    public void clear() {
        if (properties != null) {
            properties.clear();
        }
    }
}
