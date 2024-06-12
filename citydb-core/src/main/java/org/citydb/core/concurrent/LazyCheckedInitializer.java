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

import org.citydb.core.function.CheckedSupplier;

public class LazyCheckedInitializer<T, E extends Throwable> {
    private final Object lock = new Object();
    private final CheckedSupplier<T, E> supplier;
    private volatile boolean initialized = false;
    private T object = null;

    private LazyCheckedInitializer(CheckedSupplier<T, E> supplier) {
        this.supplier = supplier;
    }

    public static <T, E extends Exception> LazyCheckedInitializer<T, E> of(CheckedSupplier<T, E> supplier) {
        return new LazyCheckedInitializer<>(supplier);
    }

    public T get() throws E {
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
