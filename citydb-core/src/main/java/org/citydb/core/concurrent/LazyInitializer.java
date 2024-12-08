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

package org.citydb.core.concurrent;

import java.util.function.Supplier;

public class LazyInitializer<T> {
    private final Object lock = new Object();
    private final Supplier<T> supplier;
    private volatile boolean initialized = false;
    private T object = null;

    private LazyInitializer(Supplier<T> supplier) {
        this.supplier = supplier;
    }

    public static <T> LazyInitializer<T> of(Supplier<T> supplier) {
        return new LazyInitializer<>(supplier);
    }

    public boolean isInitialized() {
        return initialized;
    }

    public T get() {
        if (!initialized) {
            synchronized (lock) {
                if (!initialized) {
                    T result = supplier.get();
                    object = result;
                    initialized = true;
                    return result;
                }
            }
        }

        return object;
    }
}
