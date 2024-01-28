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
        return  value != null ? value : supplier.get();
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
